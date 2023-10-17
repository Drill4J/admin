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
package com.epam.drill.plugins.test2code.storage

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.TestOverview
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.*
import com.epam.dsm.find.*
import kotlinx.serialization.*

@Serializable
data class AgentKey(
    val agentId: String,
    val buildVersion: String,
) : Comparable<AgentKey> {
    override fun compareTo(other: AgentKey): Int = run {
        agentId.compareTo(other.agentId).takeIf { it != 0 } ?: buildVersion.compareTo(other.buildVersion)
    }
}

@Serializable
@StreamSerialization
internal class StoredSession(
    @Id val id: String,
    val agentKey: AgentKey,
    @Id val sessionHolderId: String,
    val testType: String,
    val testName: String,
    val tests: Set<TestOverview>,
    var probes: List<ExecClassData>,
    val isFinished : Boolean
)

internal suspend fun StoreClient.loadSessions(
    holderId: String,
): List<TestSession> = trackTime("Load session") {
    findBy<StoredSession> {
        StoredSession::sessionHolderId eq holderId
    }.get().map { stored ->
        TestSession(
            id = stored.id,
            testType = stored.testType,
            testName = stored.testName,
            isFinished = stored.isFinished
        ).also { session ->
            session.addAll(stored.probes)
        }
    }
}
internal suspend fun StoreClient.storeSession(
    holderId: String,
    agentKey: AgentKey,
    session: TestSession,
) {
    trackTime("Store session") {
        store(
            StoredSession(
                id = session.id,
                agentKey = agentKey,
                sessionHolderId = holderId,
                probes = session.probes.values.map { it.values }.flatten(),
                tests = session.tests,
                testType = session.testType,
                testName = session.testName.orEmpty(),
                isFinished = session.isFinished
            )
        )
    }
}

internal suspend fun StoreClient.sessionIds(
    agentKey: AgentKey,
) = findBy<StoredSession> { StoredSession::agentKey eq agentKey }.getIds()
