package com.epam.drill.admin.writer.rawdata.table

import org.jetbrains.exposed.sql.javatime.datetime

object TestSessionTable : StringIdTable("raw_data.test_sessions") {
    val groupId = varchar("group_id",  SHORT_TEXT_LENGTH)
    val testTaskId = varchar("test_task_id", SHORT_TEXT_LENGTH).nullable()
    val startedAt = datetime("started_at")
}