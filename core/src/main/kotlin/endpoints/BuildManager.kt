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
package com.epam.drill.admin.endpoints

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.storage.*
import io.ktor.application.*
import kotlinx.collections.immutable.*
import mu.*
import org.kodein.di.*


class BuildManager(override val di: DI) : DIAware {

    private val logger = KotlinLogging.logger {}
    internal val buildStorage by instance<BuildStorage>()

    private val agentManager by instance<AgentManager>()
    private val agentDataCache by instance<AgentDataCache>()
    private val app by instance<Application>()

    init {
        buildStorage.init(persistentHashMapOf())
    }


    suspend fun addBuildInstance(
        key: AgentBuildKey,
        instanceId: String,
        session: AgentWsSession,
    ) {
        val current = buildStorage[key] ?: persistentMapOf()
        logger.info { "put new instance id '${instanceId}' with key $key instance status is ${BuildStatus.ONLINE}" }
        buildStorage.put(key, current + (instanceId to InstanceState(session, BuildStatus.ONLINE)))
    }

    fun instanceIds(
        agentId: String,
    ) = agentManager.getOrNull(agentId)?.run {
        buildStorage.targetMap[toAgentBuildKey()]
    } ?: persistentMapOf()


    internal suspend fun removeInstance(
        agentBuildKey: AgentBuildKey,
        instanceId: String,
    ) {
        val instances = buildStorage.updateValue(agentBuildKey) { instances ->
            instances[agentBuildKey]?.let { instances.put(agentBuildKey, it - instanceId) } ?: instances
        } ?: persistentMapOf()
        if (agentManager[agentBuildKey.agentId]?.agentStatus == AgentStatus.NOT_REGISTERED && instances.isEmpty()) {
            agentManager.agentStorage.remove(agentBuildKey.agentId)
            buildStorage.remove(agentBuildKey)
            logger.info { "Agent's '${agentBuildKey}' instance '${instanceId}' was disconnected, all agent info was removed" }
        } else {
            notifyBuild(agentBuildKey)
            logger.info { "Instance '${instanceId}' of Agent '${agentBuildKey}' was disconnected, alive instances count: ${instances.size}" }
        }
    }

    // TODO EPMDJ-10011 instances spamming ONLINE
    suspend fun processInstance(
        agentId: String,
        instanceId: String,
        block: suspend AgentWsSession.() -> Unit,
    ): Unit = agentManager.getOrNull(agentId)?.run {
        val buildId = toAgentBuildKey()
        getInstanceState(buildId, instanceId)?.let { instanceState ->
            updateInstanceStatus(buildId, instanceId, BuildStatus.BUSY).also { instance ->
                if (instance.all { it.value.status == BuildStatus.BUSY }) {
                    notifyBuild(buildId)
                    logger.debug { "Build $buildId is ${BuildStatus.BUSY}." }
                }
                logger.trace { "Instance $instanceId of agent $id is ${BuildStatus.BUSY}." }
            }
            try {
                block(instanceState.agentWsSession)
            } finally {
                updateInstanceStatus(buildId, instanceId, BuildStatus.ONLINE).also {
                    notifyBuild(buildId)
                    logger.debug { "Build $buildId is ${BuildStatus.ONLINE}." }
                }
            }
        } ?: logger.warn { "Instance $instanceId is not found" }
    } ?: logger.warn { "Agent $agentId not found" }

    internal fun updateInstanceStatus(
        agentBuildKey: AgentBuildKey,
        instanceId: String,
        status: BuildStatus,
    ) = getInstanceState(agentBuildKey, instanceId)?.let { instanceState ->
        buildStorage.updateValue(agentBuildKey) { instances ->
            logger.trace { "Instance $instanceId changed status, new status is $status" }
            instances[agentBuildKey]?.let {
                instances.put(agentBuildKey, it.put(instanceId, instanceState.copy(status = status)))
            } ?: instances
        }
    } ?: persistentMapOf()

    private fun getInstanceState(
        agentBuildKey: AgentBuildKey,
        instanceId: String,
    ) = buildStorage.targetMap[agentBuildKey]?.get(instanceId)

    fun buildStatus(agentId: String): BuildStatus = instanceIds(agentId).let { instances ->
        when {
            instances.isEmpty() || instances.all { it.value.status == BuildStatus.OFFLINE } -> BuildStatus.OFFLINE
            instances.any { it.value.status == BuildStatus.ONLINE } -> BuildStatus.ONLINE
            else -> BuildStatus.BUSY
        }
    }

    internal fun buildData(agentId: String): AgentData = agentDataCache.getOrPut(agentId) {
        logger.debug { "put adminData with id=$agentId" }
        AgentData(
            agentId,
            SystemSettingsDto(packages = app.drillDefaultPackages)
        )
    }

    fun agentSessions(k: String) = instanceIds(k).values.mapNotNull { state ->
        state.takeIf { it.status == BuildStatus.ONLINE }?.agentWsSession
    }

    internal suspend fun notifyBuild(agentBuildKey: AgentBuildKey) {
        buildStorage.singleUpdate(agentBuildKey)
    }
}
