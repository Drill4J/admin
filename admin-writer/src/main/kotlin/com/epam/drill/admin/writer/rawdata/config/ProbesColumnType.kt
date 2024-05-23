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

import org.jetbrains.exposed.sql.ColumnType
import org.postgresql.util.PGobject

class ProbesColumnType : ColumnType() {
    override fun sqlType(): String = "VARBIT"

    override fun valueFromDB(value: Any): Any =
        when (value) {
            is String -> stringToBooleanArray(value)
            is PGobject -> stringToBooleanArray(value.value ?: "")
            else -> value
        }

    override fun notNullValueToDB(value: Any): Any {
        if (value is BooleanArray) {
            return PGobject().apply {
                this.type = "VARBIT"
                this.value = booleanArrayToString(value)
            }
        }
        return super.notNullValueToDB(value)
    }
}

internal fun stringToBooleanArray(str: String): BooleanArray {
    return BooleanArray(str.length) { index ->
        str[index] == '1'
    }
}

internal fun booleanArrayToString(arr: BooleanArray): String {
    return arr.joinToString("") { if (it) "1" else "0" }
}
