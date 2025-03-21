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

import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object TestLaunchTable : StringIdTable("raw_data.test_launches") {
    val groupId = varchar("group_id",  SHORT_TEXT_LENGTH)
    val testDefinitionId = varchar("test_definition_id",  SHORT_TEXT_LENGTH)
    val testSessionId = varchar("test_session_id",  SHORT_TEXT_LENGTH)
    val result = varchar("result",  SHORT_TEXT_LENGTH).nullable()
    val duration = integer("duration").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
