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
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.util.*

/**
 * Initialize periodic job to recalculate coverage data and send it to the UI
 * @features Session starting, Session finishing, Sending coverage data, Scope finishing
 */
internal fun Plugin.initScope(): Boolean = sessionHolder.initRealtimeHandler { sessionChanged, sessions ->
    if (sessionChanged) {
        sendActiveSessions()
    }
//    sessions?.let {
//        val context = state.coverContext()
//        val bundleCounters = trackTime("bundleCounters") {
//            sessions.calcBundleCounters(context, bundleByTests)
//        }.also { logPoolStats() }
//        val coverageInfoSet = trackTime("coverageInfoSet") {
//            bundleCounters.calculateCoverageData(context, this)
//        }.also { logPoolStats() }
//        updateSummary { it.copy(coverage = coverageInfoSet.coverage as ScopeCoverage) }
//        sendScope()
//        coverageInfoSet.sendScopeCoverage(buildVersion, id)
//        if (sessionChanged) {
//            bundleCounters.assocTestsJob(this)
//            bundleCounters.coveredMethodsJob(id)
//        }
//    }
}

internal fun Plugin.initBundleHandler(): Boolean = sessionHolder.initBundleHandler { tests ->
    val context = state.coverContext()
    val preparedBundle = tests.keys.associateWithTo(mutableMapOf()) {
        BundleCounter.empty
    }
    val calculated = tests.mapValuesTo(preparedBundle) {
        it.value.bundle(context)
    }
    addBundleCache(calculated)
}
