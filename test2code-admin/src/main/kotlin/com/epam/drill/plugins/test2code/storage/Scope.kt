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

private val logger = logger {}
fun Sequence<FinishedScope>.enabled() = filter { it.enabled }

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
    ): Sequence<FinishedScope> = storage.executeInAsyncTransaction {
        val transaction = this
        transaction.findBy<FinishedScope> {
            FinishedScope::agentKey eq agentKey
        }.get().run {
            takeIf { withData }?.run {
                trackTime("Loading scope") {
                    transaction.findBy<ScopeDataEntity> { ScopeDataEntity::agentKey eq agentKey }.get().takeIf { it.any() }
                }
            }?.associateBy { it.id }?.let { dataMap ->
                map { it.withProbes(dataMap[it.id], storage) }
            } ?: this
        }
    }.asSequence()

    /**
     * Store the finished scope to the database
     * @param scope the finished scope
     * @features Scope finishing
     */
    suspend fun store(scope: FinishedScope) {
        storage.executeInAsyncTransaction {
            trackTime("Store FinishedScope") {
                store(scope.copy(data = ScopeData.empty))
                scope.takeIf { it.any() }?.let {
                    store(ScopeDataEntity(it.id, it.agentKey, it.data))
                }
            }
        }
    }

    suspend fun deleteById(scopeId: String): FinishedScope? = storage.executeInAsyncTransaction {
        findById<FinishedScope>(id = scopeId)?.also {
            this.deleteById<FinishedScope>(scopeId)
            this.deleteById<ScopeDataEntity>(scopeId)
        }
    }

    suspend fun deleteByVersion(agentKey: AgentKey) {
        storage.executeInAsyncTransaction {
            deleteBy<FinishedScope> { FinishedScope::agentKey eq agentKey }
            deleteBy<ScopeDataEntity> { ScopeDataEntity::agentKey eq agentKey }
        }
    }

    suspend fun byId(
        scopeId: String,
        withProbes: Boolean = false,
    ): FinishedScope? = storage.run {
        takeIf { withProbes }?.executeInAsyncTransaction {
            findById<FinishedScope>(scopeId)?.run {
                withProbes(findById(scopeId), storage)
            }
        } ?: findById(scopeId)
    }

    internal suspend fun counter(agentKey: AgentKey): ActiveScopeInfo? = storage.findById(agentKey)

    internal suspend fun storeCounter(activeScopeInfo: ActiveScopeInfo) = storage.store(activeScopeInfo)
}

@Serializable
@StreamSerialization
internal class ScopeDataEntity(
    @Id val id: String,
    val agentKey: AgentKey,
    val bytes: ScopeData,
)

private suspend fun FinishedScope.withProbes(
    data: ScopeDataEntity?,
    storeClient: StoreClient,
): FinishedScope = data?.let {
    val scopeData: ScopeData = it.bytes
    val sessions = storeClient.loadSessions(id)
    logger.debug { "take scope $id $name with sessions size ${sessions.size}" }
    copy(data = scopeData.copy(sessions = sessions))
} ?: this
