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

import com.epam.drill.plugins.test2code.common.api.Probes
import org.jetbrains.exposed.sql.*
import org.postgresql.util.PGobject
import java.util.*

class ProbesColumnType : ColumnType() {
    override fun sqlType(): String = "VARBIT"

    override fun valueFromDB(value: Any): Any =
        when (value) {
            is String -> value.toProbes()
            is PGobject -> (value.value ?: "").toProbes()
            else -> value
        }

    override fun notNullValueToDB(value: Any): Any {
        if (value is BitSet) {
            return PGobject().apply {
                this.type = "VARBIT"
                this.value = value.toProbesString()
            }
        }
        return super.notNullValueToDB(value)
    }
}

internal fun String.toProbes(): Probes {
    val bitSet = BitSet(this.length + 1)
    for (i in this.indices) {
        if (this[i] == '1') {
            bitSet.set(i, true)
        }
    }
    bitSet.set(this.length, true) // set true indicating original array end
    return bitSet
}

internal fun Probes.toProbesString(): String {
    val builder = StringBuilder(length())
    // exclude last bit (always '1')
    for (i in 0 until length() - 1) {
        builder.append(if (get(i)) '1' else '0')
    }
    return builder.toString()
}