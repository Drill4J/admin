package com.epam.drill.admin.writer.rawdata.table

import org.jetbrains.exposed.dao.id.IntIdTable

object AstMethodTable : IntIdTable("raw_data.ast_method") {
    val instanceId = varchar("instance_id", SHORT_TEXT_LENGTH) // use reference
    val className = varchar("class_name",  LONG_TEXT_LENGTH)
    val name = varchar("name",  LONG_TEXT_LENGTH)
    val params = varchar("params",  LONG_TEXT_LENGTH) // logically, it could be longer
    val returnType = varchar("return_type",  LONG_TEXT_LENGTH)
    val bodyChecksum = varchar("body_checksum",  20) // crc64 stringified hash
    val probesCount = integer("probes_count")
    val probesStartPos = integer("probe_start_pos")
}