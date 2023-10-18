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

private val logger = logger {}

/**
 * @param id the sessionHolder ID
 * @param agentKey todo
 */
data class SessionHolder(
    @Id override val id: String = genUuid(),
    override val agentKey: AgentKey,
) : ISessionHolder {

    val sessions = AtomicCache<String, TestSession>()

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

    fun cancelSession(
        sessionId: String,
    ): TestSession? = removeSession(sessionId)

    fun cancelAllSessions() = sessions.clear()

    /**
     * Close the session-holder:
     * - clear the active sessions
     * - suspend all the session-holder jobs
     * @features Session saving
     */
    fun close() {
        logger.debug { "closing session-holder $id..." }
        sessions.clear()
    }

    override fun toString() = "session-holder($id)"

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
    val name: String = "",
    val startedAt: Long = 0L,
)
