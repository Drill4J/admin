package com.epam.drill.admin.writer.rawdata.table

import org.jetbrains.exposed.dao.id.IntIdTable

object TestMetadataTable : IntIdTable("raw_data.test_metadata") {
    val testId = varchar("test_id",  SHORT_TEXT_LENGTH)
    val name = varchar("name",  MEDIUM_TEXT_LENGTH)
    val type = varchar("type",  SHORT_TEXT_LENGTH)
}