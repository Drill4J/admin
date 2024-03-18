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
package com.epam.drill.admin.writer.rawdata.service

import com.epam.drill.admin.writer.rawdata.entity.AstEntityData
import com.epam.drill.admin.writer.rawdata.entity.RawCoverageData
import com.epam.drill.common.agent.configuration.AgentMetadata

interface RawDataReader {
    suspend fun getAgentConfigs(agentId: String, buildVersion: String): List<AgentMetadata>
    suspend fun getAstEntities(agentId: String, buildVersion: String): List<AstEntityData>
    suspend fun getRawCoverageData(agentId: String, buildVersion: String): List<RawCoverageData>
}
