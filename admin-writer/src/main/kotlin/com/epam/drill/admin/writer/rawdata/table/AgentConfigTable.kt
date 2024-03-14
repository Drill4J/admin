package com.epam.drill.admin.writer.rawdata.table

import org.jetbrains.exposed.dao.id.IntIdTable

object AgentConfigTable : IntIdTable("raw_data.agent_config") {
    val agentId = varchar("agent_id",  255)
    val instanceId = varchar("instance_id",  SHORT_TEXT_LENGTH)
    val serviceGroupId = varchar("service_group_id",  SHORT_TEXT_LENGTH)
    val buildVersion = varchar("build_version",  SHORT_TEXT_LENGTH)
    val agentType = varchar("agent_type",  SHORT_TEXT_LENGTH)
    val agentVersion = varchar("agent_version",  SHORT_TEXT_LENGTH).nullable()
}