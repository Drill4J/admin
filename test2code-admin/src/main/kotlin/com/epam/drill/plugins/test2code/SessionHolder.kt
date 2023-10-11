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
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import java.lang.ref.*

interface ISessionHolder : Sequence<Session> {
    val id: String
    val agentKey: AgentKey
}


typealias SoftBundleByTests = SoftReference<PersistentMap<TestKey, BundleCounter>>

typealias BundleCacheHandler = suspend SessionHolder.(Map<TestKey, Sequence<ExecClassData>>) -> Unit

private val logger = logger {}

/**
 * @param id the sessionHolder ID
 * @param agentKey todo
 */
data class SessionHolder(
    @Id override val id: String = genUuid(),
    override val agentKey: AgentKey,
    ) : ISessionHolder {
    private val _bundleByTests = atomic<SoftBundleByTests>(SoftReference(persistentMapOf()))

    val sessions = AtomicCache<String, TestSession>()

    private val _bundleCacheHandler = atomic<BundleCacheHandler?>(null)

    private val bundleCacheJob = AsyncJobDispatcher.launch {
        while (true) {
            val tests = sessions.values.flatMap { it.updatedTests }
            _bundleCacheHandler.value.takeIf { tests.any() }?.let {
                val probes = this@SessionHolder + sessions.values
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

    fun initBundleHandler(handler: BundleCacheHandler): Boolean = _bundleCacheHandler.getAndUpdate {
        it ?: handler
    } == null

    override fun iterator(): Iterator<Session> = sessions.values.iterator()

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
    ) = TestSession(sessionId, testType, isGlobal, isRealtime, testName, labels).takeIf { newSession ->
        sessions(sessionId) { existing ->
            existing ?: newSession.takeIf { sessions[it.id] == null }
        } === newSession
    }


    fun addProbes(
        sessionId: String,
        probeProvider: () -> Collection<ExecClassData>,
    ): TestSession? = sessions[sessionId]?.apply { addAll(probeProvider()) }

    fun addBundleCache(bundleByTests: Map<TestKey, BundleCounter>) {
        _bundleByTests.update {
            val bundles = (it.get() ?: persistentMapOf()).putAll(bundleByTests)
            SoftReference(bundles)
        }
    }

    fun cancelSession(
        sessionId: String,
    ): TestSession? = removeSession(sessionId)?.also {
        clearBundleCache()
    }

    fun cancelAllSessions() = sessions.clear().also {
        clearBundleCache()
    }

    /**
     * Finish the test session
     * @param sessionId the test session ID
     * @return the finished session
     * @features Session finishing
     */
    fun finishSession(
        sessionId: String,
    ): TestSession? = sessions[sessionId]?.run {
            val execData = this.probes.values.map { it.values }.flatten()
            if (execData.any()) {
                _bundleByTests.update {
                    val current = it.get() ?: persistentMapOf()
                    SoftReference(current - updatedTests)
                }
            }
        this
    }


    /**
     * Close the session-holder:
     * - clear the active sessions
     * - suspend all the session-holder jobs
     * @features Scope finishing
     */
    fun close() {
        logger.debug { "closing session-holder $id..." }
        sessions.clear()
        bundleCacheJob.cancel()
    }

    override fun toString() = "session-holder($id)"

    private fun clearBundleCache() = _bundleByTests.update { SoftReference(persistentMapOf()) }

    private fun removeSession(id: String): TestSession? = sessions.run {
        val testSession = get(id)
        this.remove(id)
        testSession
    }
}

@Serializable
internal data class SessionHolderInfo(
    @Id val agentKey: AgentKey,
    val id: String = genUuid(),
    val nth: Int = 1,
    val name: String = "",
    val startedAt: Long = 0L,
)

internal fun SessionHolderInfo.inc() = copy(nth = nth.inc())
