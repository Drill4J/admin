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
    val scopeId: String,
    val data: FinishedSession,
)

internal suspend fun StoreClient.loadSessions(
    scopeId: String,
): List<FinishedSession> = trackTime("Load session") {
    findBy<StoredSession> {
        StoredSession::scopeId eq scopeId
    }.get().map { it.data }
}

internal suspend fun StoreClient.storeSession(
    scopeId: String,
    agentKey: AgentKey,
    session: FinishedSession,
) {
    trackTime("Store session") {
        store(
            StoredSession(
                id = genUuid(),
                agentKey = agentKey,
                scopeId = scopeId,
                data = session
            )
        )
    }
}

internal suspend fun StoreClient.sessionIds(
    agentKey: AgentKey,
) = findBy<StoredSession> { StoredSession::agentKey eq agentKey }.getIds()
