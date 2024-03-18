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

import org.jetbrains.exposed.dao.id.IntIdTable

object TestMetadataTable : IntIdTable("raw_data.test_metadata") {
    val testId = varchar("test_id",  SHORT_TEXT_LENGTH)
    val name = varchar("name",  MEDIUM_TEXT_LENGTH)
    val type = varchar("type",  SHORT_TEXT_LENGTH)
}