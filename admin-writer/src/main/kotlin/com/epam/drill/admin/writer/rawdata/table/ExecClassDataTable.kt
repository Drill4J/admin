package com.epam.drill.admin.writer.rawdata.table

import com.epam.drill.admin.writer.rawdata.config.BitSetColumnType
import org.jetbrains.exposed.dao.id.IntIdTable
import java.util.*

object ExecClassDataTable : IntIdTable("raw_data.exec_class_data") {
    val instanceId = varchar("instance_id", SHORT_TEXT_LENGTH) // use reference
    val className = varchar("class_name",  LONG_TEXT_LENGTH)
    val testId = varchar("test_id",  SHORT_TEXT_LENGTH)
    val probes = registerColumn<BitSet>("probes", BitSetColumnType())
}