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

object MethodTable : StringIdTable("raw_data.ast_method") {
    val classname = varchar("classname",  LONG_TEXT_LENGTH)
    val name = varchar("name",  LONG_TEXT_LENGTH)
    val params = varchar("params",  LONG_TEXT_LENGTH) // logically, it could be longer
    val returnType = varchar("return_type",  LONG_TEXT_LENGTH)
    val bodyChecksum = varchar("body_checksum",  20) // crc64 stringified hash
    val probesCount = integer("probes_count")
    val probesStartPos = integer("probe_start_pos")
}