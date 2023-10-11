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
import kotlinx.atomicfu.update
import kotlinx.serialization.*

private val logger = logger {}

/**
 * The service for managing scopes
 *
 * @param storage the plugin's datasource client
 */
class ScopeManager(private val storage: StoreClient) {

    /**
     * Load finished scopes by the build version
     * @param agentKey the pair of agent ID and the build version
     * @param withData the sign of the need to load scope data
     * @return a sequences of finished scopes
     * @features Agent registration, Scope finishing
     */
    suspend fun byVersion(
        agentKey: AgentKey,
        withData: Boolean = false,
    ): Sequence<SessionHolder> = storage.executeInAsyncTransaction {
        val transaction = this
        transaction.findBy<SessionHolder> {
            SessionHolder::agentKey eq agentKey
        }.get().run {
            takeIf { withData }?.run {
                trackTime("Loading scope") {
                    transaction.findBy<ScopeDataEntity> { ScopeDataEntity::agentKey eq agentKey }.get()
                        .takeIf { it.any() }
                }
            }?.associateBy { it.id }?.let { dataMap ->
                map { it.withProbes(dataMap[it.id], storage) }
            } ?: this
        }
    }.asSequence()

    suspend fun deleteById(scopeId: String): SessionHolder? = storage.executeInAsyncTransaction {
        findById<SessionHolder>(id = scopeId)?.also {
            this.deleteById<SessionHolder>(scopeId)
            this.deleteById<ScopeDataEntity>(scopeId)
        }
    }

    suspend fun deleteByVersion(agentKey: AgentKey) {
        storage.executeInAsyncTransaction {
            deleteBy<SessionHolder> { SessionHolder::agentKey eq agentKey }
            deleteBy<ScopeDataEntity> { ScopeDataEntity::agentKey eq agentKey }
        }
    }

    internal suspend fun counter(agentKey: AgentKey): SessionHolderInfo? = storage.findById(agentKey)

    internal suspend fun storeCounter(sessionHolderInfo: SessionHolderInfo) = storage.store(sessionHolderInfo)
}

@Serializable
@StreamSerialization
internal class ScopeDataEntity(
    @Id val id: String,
    val agentKey: AgentKey,
    val bytes: List<FinishedSession>,
)

private suspend fun SessionHolder.withProbes(
    data: ScopeDataEntity?,
    storeClient: StoreClient,
): SessionHolder = data?.let {
    val scopeData: List<FinishedSession> = it.bytes
    val sessions = storeClient.loadSessions(id)
    logger.debug { "take scope $id  with sessions size ${sessions.size}" }

    this.apply {
       finishedSessions.update { list -> list + scopeData + sessions }
    }
} ?: this
