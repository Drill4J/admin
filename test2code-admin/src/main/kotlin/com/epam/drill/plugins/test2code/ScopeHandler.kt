/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.routes.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.util.*

/**
 * Initialize periodic job to recalculate coverage data and send it to the UI
 * @features Session starting, Session finishing, Sending coverage data, Scope finishing
 */
internal fun Plugin.initActiveScope(): Boolean = activeScope.initRealtimeHandler { sessionChanged, sessions ->
    if (sessionChanged) {
        sendActiveSessions()
    }
    sessions?.let {
        val context = state.coverContext()
        val bundleCounters = trackTime("bundleCounters") {
            sessions.calcBundleCounters(context, bundleByTests)
        }.also { logPoolStats() }
        val coverageInfoSet = trackTime("coverageInfoSet") {
            bundleCounters.calculateCoverageData(context, this)
        }.also { logPoolStats() }
        updateSummary { it.copy(coverage = coverageInfoSet.coverage as ScopeCoverage) }
        sendActiveScope()
        coverageInfoSet.sendScopeCoverage(buildVersion, id)
        if (sessionChanged) {
            bundleCounters.assocTestsJob(this)
            bundleCounters.coveredMethodsJob(id)
        }
    }
}

internal fun Plugin.initBundleHandler(): Boolean = activeScope.initBundleHandler { tests ->
    val context = state.coverContext()
    val preparedBundle = tests.keys.associateWithTo(mutableMapOf()) {
        BundleCounter.empty
    }
    val calculated = tests.mapValuesTo(preparedBundle) {
        it.value.bundle(context)
    }
    addBundleCache(calculated)
}


/**
 * Finish the current active scope and start a new one
 * @param scopeChange the action payload to finish the scope
 * @features Scope finishing
 */
internal suspend fun Plugin.changeActiveScope(
    scopeChange: ActiveScopeChangePayload,
): ActionResult = if (state.scopeByName(scopeChange.scopeName) == null) {
    trackTime("changeActiveScope") {
        if (scopeChange.forceFinish) {
            val activeScope = state.activeScope
            logger.warn { "Active sessions count ${activeScope.activeSessions.count()}. Probes may be lost." }
            val probes = activeScope.activeSessions.map.mapNotNull { (sessionId, _) ->
                state.finishSession(sessionId)
            }.asSequence().flatten()
            if (probes.any()) calculateAndSendScopeCoverage()
        }
        val prevScope = state.changeActiveScope(scopeChange.scopeName.trim())
        state.storeActiveScopeInfo()
        sendActiveSessions()
        sendActiveScope()
        if (scopeChange.savePrevScope) {
            if (prevScope.any()) {
                logger.debug { "finish scope with id=${prevScope.id}" }.also { logPoolStats() }
                val finishedScope = prevScope.finish(scopeChange.prevScopeEnabled)
                state.scopeManager.store(finishedScope)
                sendScopeSummary(finishedScope.summary)
                logger.info { "$finishedScope has been saved." }.also { logPoolStats() }
            } else {
                cleanTopics(prevScope.id)
                logger.info { "$prevScope is empty, it won't be added to the build." }.also { logPoolStats() }
            }
        } else cleanTopics(prevScope.id)
        scopeInitialized(prevScope.id)
        ActionResult(StatusCodes.OK,
            data = ActionScopeResult(
                id = activeScope.id,
                name = activeScope.name,
                prevId = prevScope.id
            )
        )
    }
} else ActionResult(
    code = StatusCodes.CONFLICT,
    data = "Failed to switch to a new scope: name ${scopeChange.scopeName} is already in use"
)

/**
 * Initialize the active scope:
 * - resume the jobs for a new active scope
 * - recalculate coverage data by the enabled finished scopes
 * - calculate coverage data for the active scope
 * @param prevId the previous scope ID
 * @features Scope finishing
 */
internal suspend fun Plugin.scopeInitialized(prevId: String) {
    if (initActiveScope() && initBundleHandler()) {
        sendScopes()
        val prevScope = state.scopeManager.byId(prevId)
        prevScope?.takeIf { it.enabled }.apply {
            calculateAndSendBuildCoverage()
        }
        calculateAndSendScopeCoverage()
        logger.info { "Current active scope - $activeScope" }.also { logPoolStats() }
    }
}

internal suspend fun Plugin.renameScope(
    payload: RenameScopePayload,
): ActionResult = state.scopeById(payload.scopeId)?.let { scope ->
    val scopeName = payload.scopeName
    if (state.scopeByName(scopeName) == null) {
        state.renameScope(payload.scopeId, scopeName)?.let { summary ->
            sendScopeMessages(scope.agentKey.buildVersion)
            sendScopeSummary(summary, scope.agentKey.buildVersion)
        }
        ActionResult(StatusCodes.OK, "Renamed scope with id ${payload.scopeId} -> $scopeName")
    } else ActionResult(
        StatusCodes.CONFLICT,
        "Scope with such name already exists. Please choose a different name."
    )
} ?: ActionResult(
    StatusCodes.NOT_FOUND,
    "Failed to rename scope with id ${payload.scopeId}: scope not found"
)

internal suspend fun Plugin.toggleScope(scopeId: String): ActionResult {
    state.toggleScope(scopeId)
    return state.scopeManager.byId(scopeId)?.let { scope ->
        handleChange(scope)
        ActionResult(
            StatusCodes.OK,
            "Scope with id $scopeId toggled to 'enabled' value '${scope.enabled}'"
        )
    } ?: ActionResult(
        StatusCodes.NOT_FOUND,
        "Failed to toggle scope with id $scopeId: scope not found"
    )
}

internal suspend fun Plugin.dropScope(scopeId: String): ActionResult {
    return state.scopeManager.deleteById(scopeId)?.let { scope ->
        cleanTopics(scope.id)
        handleChange(scope)
        ActionResult(
            StatusCodes.OK,
            "Scope with id $scopeId was removed"
        )
    } ?: ActionResult(
        StatusCodes.NOT_FOUND,
        "Failed to drop scope with id $scopeId: scope not found"
    )
}

/**
 * Clean the scope data which was sent to the UI
 * @param scopeId the scope ID which data need to clean
 * @features Scope finishing
 */
private suspend fun Plugin.cleanTopics(scopeId: String) = scopeById(scopeId).let { scope ->
    val coverageRoute = Routes.Build.Scopes.Scope.Coverage(scope)
    send(buildVersion, coverageRoute, "")
    state.classDataOrNull()?.let { classData ->
        val pkgsRoute = Routes.Build.Scopes.Scope.Coverage.Packages(coverageRoute)
        classData.packageTree.packages.forEach { p ->
            send(buildVersion, Routes.Build.Scopes.Scope.Coverage.Packages.Package(p.name, pkgsRoute), "")
            send(buildVersion, Routes.Build.Scopes.Scope.AssociatedTests(p.id, scope), "")
            p.classes.forEach { c ->
                send(buildVersion, Routes.Build.Scopes.Scope.AssociatedTests(c.id, scope), "")
                c.methods.forEach { m ->
                    send(buildVersion, Routes.Build.Scopes.Scope.AssociatedTests(m.id, scope), "")
                }
            }
        }
    }
    send(buildVersion, Routes.Build.Scopes.Scope.Tests(scope), "")
}

private suspend fun Plugin.handleChange(scope: FinishedScope) {
    val scopeBuildVersion = scope.agentKey.buildVersion
    if (scopeBuildVersion == buildVersion) {
        calculateAndSendBuildCoverage()
        activeScope.probesChanged()
    }
    sendScopes(scopeBuildVersion)
    sendScopeSummary(scope.summary, scopeBuildVersion)
}
