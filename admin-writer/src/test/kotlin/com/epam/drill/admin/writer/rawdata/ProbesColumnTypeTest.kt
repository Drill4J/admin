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
package com.epam.drill.admin.writer.rawdata

import com.epam.drill.admin.writer.rawdata.config.ProbesColumnType
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ProbesColumnTypeTest : DatabaseTests() {

    @BeforeEach
    fun initSchema() {
        withRollback {
            create(BoolArrays)
        }
    }

    @Test
    fun `test storing and retrieving Probes`() = withRollback {
        val originalProbes = booleanArrayOf(true, false, true, false, false, true)

        BoolArrays.insert {
            it[boolArrays] = originalProbes
        }
        val retrievedProbes = BoolArrays.selectAll().single()[BoolArrays.boolArrays]

        assertTrue(originalProbes.contentEquals(retrievedProbes))
    }

}

object BoolArrays : IntIdTable() {
    val boolArrays = registerColumn<BooleanArray>("bool_arrays", ProbesColumnType())
}