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

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object BuildMethodTable : Table("raw_data.build_methods") {
    val groupId = varchar("group_id", SHORT_TEXT_LENGTH)
    val appId = varchar("app_id", SHORT_TEXT_LENGTH)
    val methodId = varchar("method_id", MEDIUM_TEXT_LENGTH).references(MethodTable.methodId).nullable()
    val buildId = varchar("build_id", MEDIUM_TEXT_LENGTH)
    val probesStartPos = integer("probe_start_pos")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(buildId, methodId, appId, groupId)
}