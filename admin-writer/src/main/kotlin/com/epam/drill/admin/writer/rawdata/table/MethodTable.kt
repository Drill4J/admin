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

object MethodTable : Table("raw_data.methods") {
    val methodId = varchar("method_id", MEDIUM_TEXT_LENGTH)
    val groupId = varchar("group_id", SHORT_TEXT_LENGTH)
    val appId = varchar("app_id", SHORT_TEXT_LENGTH)
    val classname = varchar("class_name",  LONG_TEXT_LENGTH)
    val name = varchar("method_name",  LONG_TEXT_LENGTH)
    val params = varchar("method_params",  LONG_TEXT_LENGTH) // logically, it could be longer
    val returnType = varchar("return_type",  LONG_TEXT_LENGTH)
    val bodyChecksum = varchar("body_checksum",  SHORT_TEXT_LENGTH) // crc64 stringified hash
    var signature = varchar("signature", LONG_TEXT_LENGTH)
    val probesCount = integer("probes_count")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(methodId, appId, groupId)
}
