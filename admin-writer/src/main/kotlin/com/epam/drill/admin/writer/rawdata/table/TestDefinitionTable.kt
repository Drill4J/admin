package com.epam.drill.admin.writer.rawdata.table

object TestDefinitionTable : StringIdTable("raw_data.test_definitions") {
    val groupId = varchar("group_id",  SHORT_TEXT_LENGTH)
    val type = varchar("type",  SHORT_TEXT_LENGTH).nullable()
    val runner = varchar("runner", SHORT_TEXT_LENGTH).nullable()
    val name = varchar("name",  MEDIUM_TEXT_LENGTH).nullable()
    val path = varchar("path",  MEDIUM_TEXT_LENGTH).nullable()
}