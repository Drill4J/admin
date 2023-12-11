package com.epam.drill.admin.auth.table

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

object ApiKeyTable : IntIdTable(name = "auth.api_key") {
    val userId = integer("user_id").references(UserTable.id)
    val description = varchar("description", 200)
    val apiKeyHash = varchar("api_key_hash", 100)
    var expiresAt = datetime("expires_at")
    var createdAt = datetime("created_at")
}