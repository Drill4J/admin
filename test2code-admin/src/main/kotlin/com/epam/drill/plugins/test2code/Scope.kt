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

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.routes.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.*
import com.epam.dsm.util.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import java.lang.ref.*

interface Scope : Sequence<FinishedSession> {
    val id: String
    val agentKey: AgentKey
    val name: String
    val summary: ScopeSummary
}

fun Sequence<Scope>.summaries(): List<ScopeSummary> = map(Scope::summary).toList()

typealias SoftBundleByTests = SoftReference<PersistentMap<TestKey, BundleCounter>>

typealias CoverageHandler = suspend ActiveScope.(Boolean, Sequence<Session>?) -> Unit

typealias BundleCacheHandler = suspend ActiveScope.(Map<TestKey, Sequence<ExecClassData>>) -> Unit

private val logger = logger {}

/**
 * State of an active scope
 * @param id the scope ID
 * @param agentKey todo
 * @param nth todo
 * @param name the scope name
 * @param sessions all finished sessions in the scope
 */
class ActiveScope(
    override val id: String = genUuid(),
    override val agentKey: AgentKey,
    val nth: Int = 1,
    name: String = "$DEFAULT_SCOPE_NAME $nth".weakIntern(),
    sessions: List<FinishedSession> = emptyList(),
) : Scope {
    private val _bundleByTests = atomic<SoftBundleByTests>(SoftReference(persistentMapOf()))

    val bundleByTests: PersistentMap<TestKey, BundleCounter>
        get() = _bundleByTests.value.get() ?: persistentMapOf()

    private enum class Change(val sessions: Boolean, val probes: Boolean) {
        ONLY_SESSIONS(true, false),
        ONLY_PROBES(false, true),
        ALL(true, true)
    }

    override val summary get() = _summary.value

    override val name get() = summary.name

    val activeSessions = AtomicCache<String, ActiveSession>()

    private val _sessions = atomic(sessions.toMutableList())

    //TODO remove summary for this class
    private val _summary = atomic(
        ScopeSummary(
            id = id,
            name = name,
            started = currentTimeMillis(),
            sessionsFinished = sessions.size,
        )
    )

    private val _realtimeCoverageHandler = atomic<CoverageHandler?>(null)
    private val _bundleCacheHandler = atomic<BundleCacheHandler?>(null)


    private val _change = atomic<Change?>(null)

    private val realtimeCoverageJob = AsyncJobDispatcher.launch {
        while (true) {
            delay(250)
            _change.value?.let {
                delay(250)
                _change.getAndUpdate { null }?.let { change ->
                    _realtimeCoverageHandler.value?.let { handler ->
                        val probes = if (change.probes) {
                            this@ActiveScope + activeSessions.values.filter { it.isRealtime }
                        } else null
                        handler(change.sessions, probes)
                        delay(500)
                    }
                }
            }
        }
    }

    private val bundleCacheJob = AsyncJobDispatcher.launch {
        while (true) {
            val tests = activeSessions.values.flatMap { it.updatedTests }
            _bundleCacheHandler.value.takeIf { tests.any() }?.let {
                val probes = this@ActiveScope + activeSessions.values
                val probesByTests = probes.groupBy { it.testType }.map { (testType, sessions) ->
                    sessions.asSequence().flatten()
                        .groupBy { it.testId.testKey(testType) }
                        .filter { it.key in tests }
                        .mapValuesTo(mutableMapOf()) { it.value.asSequence() }
                }.takeIf { it.isNotEmpty() }?.reduce { m1, m2 ->
                    m1.apply { putAll(m2) }
                } ?: emptyMap()
                it(probesByTests)
            }
            delay(2000)
        }
    }

    fun initRealtimeHandler(handler: CoverageHandler): Boolean = _realtimeCoverageHandler.getAndUpdate {
        it ?: handler.also { _change.value = Change.ALL }
    } == null

    fun initBundleHandler(handler: BundleCacheHandler): Boolean = _bundleCacheHandler.getAndUpdate {
        it ?: handler
    } == null

    //TODO remove summary related stuff from the active scope
    fun updateSummary(updater: (ScopeSummary) -> ScopeSummary) = _summary.updateAndGet(updater)

    fun rename(name: String): ScopeSummary = _summary.updateAndGet { it.copy(name = name) }

    /**
     * Create a FinishScope instance from the active scope state
     * @param enabled todo
     * @features Scope finishing
     */
    fun finish(enabled: Boolean) = FinishedScope(
        id = id,
        agentKey = agentKey,
        name = summary.name,
        enabled = enabled,
        summary = summary.copy(
            finished = currentTimeMillis(),
            active = false,
            enabled = enabled
        ),
        data = ScopeData(
            sessions = toList(),
        )
    )

    override fun iterator(): Iterator<FinishedSession> = _sessions.value.iterator()

    /**
     * Start the test session
     * @features Session starting
     */
    fun startSession(
        sessionId: String,
        testType: String,
        isGlobal: Boolean = false,
        isRealtime: Boolean = false,
        testName: String? = null,
        labels: Set<Label> = emptySet(),
    ) = ActiveSession(sessionId, testType, isGlobal, isRealtime, testName, labels).takeIf { newSession ->
        val key = sessionId
        activeSessions(key) { existing ->
            existing ?: newSession.takeIf { activeSessions[it.id] == null }
        } === newSession
    }?.also {
        sessionsChanged()
    }


    fun addProbes(
        sessionId: String,
        probeProvider: () -> Collection<ExecClassData>,
    ): ActiveSession? = activeSessions[sessionId]?.apply { addAll(probeProvider()) }

    fun addBundleCache(bundleByTests: Map<TestKey, BundleCounter>) {
        _bundleByTests.update {
            val bundles = (it.get() ?: persistentMapOf()).putAll(bundleByTests)
            SoftReference(bundles)
        }
    }

    fun probesChanged() = _change.update {
        when (it) {
            Change.ONLY_SESSIONS, Change.ALL -> Change.ALL
            else -> Change.ONLY_PROBES
        }
    }

    fun cancelSession(
        sessionId: String,
    ): ActiveSession? = removeSession(sessionId)?.also {
        clearBundleCache()
        if (it.any()) {
            _change.value = Change.ALL
        } else sessionsChanged()
    }

    fun cancelAllSessions() = activeSessions.clear().also { map ->
        clearBundleCache()
        if (map.values.any { it.any() }) {
            _change.value = Change.ALL
        } else sessionsChanged()
    }

    /**
     * Finish the test session
     * @param sessionId the test session ID
     * @return the finished session
     * @features Session finishing
     */
    fun finishSession(
        sessionId: String,
    ): FinishedSession? = removeSession(sessionId)?.run {
        finish().also { finished ->
            if (finished.probes.any()) {
                val updatedSessions = _sessions.updateAndGet { it.apply { add(finished) } }
                _bundleByTests.update {
                    val current = it.get() ?: persistentMapOf()
                    SoftReference(current - updatedTests)
                }
                _summary.update { it.copy(sessionsFinished = updatedSessions.count()) }
                _change.value = Change.ALL
            } else sessionsChanged()
        }
    }


    /**
     * Close the active scope:
     * - clear the active sessions
     * - suspend all the scope jobs
     * @features Scope finishing
     */
    fun close() {
        logger.debug { "closing active scope $id..." }
        _change.value = null
        activeSessions.clear()
        realtimeCoverageJob.cancel()
        bundleCacheJob.cancel()
    }

    override fun toString() = "act-scope($id, $name)"

    /**
     * Set a sign that the session has been changed
     * @features Session starting
     */
    private fun sessionsChanged() {
        _change.update {
            when (it) {
                Change.ONLY_PROBES, Change.ALL -> Change.ALL
                else -> Change.ONLY_SESSIONS
            }
        }
    }

    private fun clearBundleCache() = _bundleByTests.update { SoftReference(persistentMapOf()) }

    private fun removeSession(id: String): ActiveSession? = activeSessions.run {
        remove(id)
    }
}

@Serializable
data class ScopeData(
    @Transient
    val sessions: List<FinishedSession> = emptyList(),
) {
    companion object {
        val empty = ScopeData()
    }
}

@Serializable
data class FinishedScope(
    @Id override val id: String,
    override val agentKey: AgentKey,
    override val name: String,
    override val summary: ScopeSummary,
    val enabled: Boolean,
    val data: ScopeData,
) : Scope {
    override fun iterator() = data.sessions.iterator()

    override fun toString() = "fin-scope($id, $name)"
}

@Serializable
internal data class ActiveScopeInfo(
    @Id val agentKey: AgentKey,
    val id: String = genUuid(),
    val nth: Int = 1,
    val name: String = "",
    val startedAt: Long = 0L,
)

internal fun ActiveScopeInfo.inc() = copy(nth = nth.inc())

fun scopeById(scopeId: String) = Routes.Build.Scopes(Routes.Build()).let {
    Routes.Build.Scopes.Scope(scopeId, it)
}
