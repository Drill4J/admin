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
package com.epam.drill.admin.agent.config

import com.epam.drill.admin.store.*
import com.epam.drill.common.agent.configuration.*
import org.kodein.di.*

class ConfigHandler(override val di: DI) : DIAware {

    /**
     * Store parameters of the agent
     * @param agentId the agent ID
     * @param parameters agent parameters
     * @features Agent attaching
     */
    suspend fun store(agentId: String, parameters: Map<String, AgentParameter>) {
        adminStore.store(StoredAgentConfig(agentId, parameters))
    }

    suspend fun load(agentId: String) = adminStore.findById<StoredAgentConfig>(agentId)?.params

    suspend fun remove(agentId: String) = adminStore.deleteById<StoredAgentConfig>(agentId)
}

