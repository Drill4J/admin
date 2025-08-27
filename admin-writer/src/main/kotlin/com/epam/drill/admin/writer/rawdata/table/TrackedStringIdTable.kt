package com.epam.drill.admin.writer.rawdata.table

import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime


abstract class TrackedStringIdTable(name: String = "", columnName: String = "id") : StringIdTable(name) {
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val createdBy = varchar("created_by", 100).nullable()
}