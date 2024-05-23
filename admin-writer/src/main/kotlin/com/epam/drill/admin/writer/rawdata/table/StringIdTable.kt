package com.epam.drill.admin.writer.rawdata.table

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column

abstract class StringIdTable(name: String = "", columnName: String = "id") : IdTable<String>(name) {
    final override val id: Column<EntityID<String>> = varchar(columnName, SHORT_TEXT_LENGTH).entityId()
    final override val primaryKey = PrimaryKey(id)
}