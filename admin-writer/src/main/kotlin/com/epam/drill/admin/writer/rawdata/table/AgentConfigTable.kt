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
package com.epam.drill.admin.writer.rawdata.table

import org.jetbrains.exposed.dao.id.IntIdTable

object AgentConfigTable : IntIdTable("raw_data.agent_config") {
    val agentId = varchar("agent_id",  255)
    val instanceId = varchar("instance_id",  SHORT_TEXT_LENGTH)
    val serviceGroupId = varchar("service_group_id",  SHORT_TEXT_LENGTH)
    val buildVersion = varchar("build_version",  SHORT_TEXT_LENGTH)
    val agentType = varchar("agent_type",  SHORT_TEXT_LENGTH)
    val agentVersion = varchar("agent_version",  SHORT_TEXT_LENGTH).nullable()
    val vcsMetadataHash = varchar("vcs_metadata_hash",  SHORT_TEXT_LENGTH).nullable()
    val vcsMetadataParents = varchar("vcs_metadata_parents",  SHORT_TEXT_LENGTH).nullable()
    val vcsMetadataBranch = varchar("vcs_metadata_branch",  SHORT_TEXT_LENGTH).nullable()
}