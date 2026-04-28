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
package com.epam.drill.admin.writer.rawdata.config

import org.jetbrains.exposed.sql.IColumnType
import org.postgresql.util.PGobject

typealias BitString = String

class ProbesColumnType(override var nullable: Boolean = false) : IColumnType<BitString> {
    override fun sqlType(): String = "VARBIT"

    override fun valueFromDB(value: Any): BitString =
        when (value) {
            is String -> value
            is PGobject -> value.value ?: ""
            else -> throw IllegalStateException("Unsupported value type: ${value::class}")
        }

    override fun notNullValueToDB(value: BitString): Any {
        return PGobject().apply {
            this.type = "VARBIT"
            this.value = value
        }
    }

}

fun BooleanArray.toBitString(): BitString {
    return joinToString(separator = "") { if (it) "1" else "0" }
}
