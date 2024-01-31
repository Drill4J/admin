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
package com.epam.drill.admin.agent.logging

import com.epam.drill.admin.api.*
import com.epam.drill.admin.store.*
import com.epam.dsm.*
import org.kodein.di.*

// TODO remove file
//todo remove after testing EPMDJ-7890
class LoggingHandler(override val di: DI) : DIAware {

    suspend fun updateConfig(agentId: String, loggingConfig: LoggingConfigDto) {
        adminStore.store(AgentLoggingConfig(agentId, loggingConfig))
    }

    private suspend fun StoreClient.loadConfig(agentId: String) = run {
        findById<AgentLoggingConfig>(agentId)
    }
}