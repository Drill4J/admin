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
package com.epam.drill.admin.etl.impl

import com.epam.drill.admin.etl.UntypedRow
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class UntypedPreparedSqlTest {

    @Test
    fun `prepareSql should replace single named parameter with question mark`() {
        val sql = "SELECT * FROM users WHERE id = :userId"

        val result = UntypedPreparedSql.prepareSql(sql)

        assertEquals("SELECT * FROM users WHERE id = ?", result.getSql())
        assertEquals(listOf(1), result.getArgs(rowOf("userId" to 1)))
    }

    @Test
    fun `prepareSql should replace multiple named parameters with question marks in right order`() {
        val sql = "SELECT * FROM users WHERE name = :userName AND age = :userAge"

        val result = UntypedPreparedSql.prepareSql(sql)

        assertEquals("SELECT * FROM users WHERE name = ? AND age = ?", result.getSql())
        assertEquals(listOf("testUser", 25), result.getArgs(rowOf("userAge" to 25, "userName" to "testUser")))
    }

    @Test
    fun `prepareSql should handle repeated named parameters`() {
        val sql = "SELECT * FROM users WHERE id = :id OR parent_id = :id"

        val result = UntypedPreparedSql.prepareSql(sql)

        assertEquals("SELECT * FROM users WHERE id = ? OR parent_id = ?", result.getSql())
        assertEquals(listOf(1, 1), result.getArgs(rowOf("id" to 1)))
    }

    @Test
    fun `prepareSql should not replace double colons`() {
        val sql = "SELECT * FROM users WHERE data::jsonb @> :filter"

        val result = UntypedPreparedSql.prepareSql(sql)

        assertEquals("SELECT * FROM users WHERE data::jsonb @> ?", result.getSql())
        assertEquals(listOf("testFilter"), result.getArgs(rowOf("filter" to "testFilter")))
    }

    @Test
    fun `prepareSql should handle SQL with no named parameters`() {
        val sql = "SELECT * FROM users WHERE id = 1"

        val result = UntypedPreparedSql.prepareSql(sql)

        assertEquals("SELECT * FROM users WHERE id = 1", result.getSql())
        assertEquals(emptyList(), result.getArgs(rowOf("id" to 1)))
    }

    @Test
    fun `prepareSql should handle parameters with underscores and numbers`() {
        val sql = "INSERT INTO table (col1, col2) VALUES (:param_1, :param2_test)"

        val result = UntypedPreparedSql.prepareSql(sql)

        assertEquals("INSERT INTO table (col1, col2) VALUES (?, ?)", result.getSql())
        assertEquals(listOf(1, 2), result.getArgs(rowOf("param_1" to 1, "param2_test" to 2)))
    }


    @Test
    fun `prepareSql should handle parameters in complex SQL with multiple clauses`() {
        val sql = """
            SELECT u.name, u.email 
            FROM users u 
            WHERE u.status = :status 
            AND u.created_at > :startDate 
            AND u.created_at < :endDate
            ORDER BY u.name
        """.trimIndent()

        val result = UntypedPreparedSql.prepareSql(sql)

        val expectedSql = """
            SELECT u.name, u.email 
            FROM users u 
            WHERE u.status = ? 
            AND u.created_at > ? 
            AND u.created_at < ?
            ORDER BY u.name
        """.trimIndent()

        assertEquals(expectedSql, result.getSql())
        assertEquals(
            listOf("SUCCESS", "2025-01-01", "2025-12-10"), result.getArgs(
                rowOf("endDate" to "2025-12-10", "status" to "SUCCESS", "startDate" to "2025-01-01")
            )
        )
    }

    @Test
    fun `getArgs should handle null values`() {
        val sql = "INSERT INTO users (name, email) VALUES (:name, :email)"
        val result = UntypedPreparedSql.prepareSql(sql)

        val row = rowOf("name" to "John", "email" to null)
        val args = result.getArgs(row)

        assertEquals(listOf("John", null), args)
    }

    @Test
    fun `getArgs should return null for missing keys`() {
        val sql = "SELECT * FROM users WHERE name = :userName AND age = :userAge"
        val result = UntypedPreparedSql.prepareSql(sql)

        val row = rowOf("userName" to "John")
        val args = result.getArgs(row)

        assertEquals(listOf("John", null), args)
    }

    private fun rowOf(vararg entry: Pair<String, Any?>) = UntypedRow(Instant.EPOCH, mapOf(*entry))

}

