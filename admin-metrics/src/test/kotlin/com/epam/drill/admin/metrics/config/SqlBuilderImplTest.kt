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
package com.epam.drill.admin.metrics.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlBuilderImplTest {
    @Test
    fun `append should add SQL fragment and parameters`() {
        val builder = SqlBuilderImpl()
        builder.append("SELECT * FROM table WHERE id = ?", 123)
        assertTrue(builder.sqlQuery.toString().contains("SELECT * FROM table WHERE id = ?"))
        assertEquals(listOf(123), builder.params.toList())
    }

    @Test
    fun `appendOptional should add SQL fragment and parameters if params are not null or blank`() {
        val builder = SqlBuilderImpl()
        builder.appendOptional("AND name = ?", "John")
        assertTrue(builder.sqlQuery.toString().contains("AND name = ?"))
        assertEquals(listOf("John"), builder.params.toList())
    }

    @Test
    fun `appendOptional should not add SQL fragment if any param is null`() {
        val builder = SqlBuilderImpl()
        builder.appendOptional("AND name = ?", null)
        assertEquals("", builder.sqlQuery.toString())
        assertEquals(emptyList(), builder.params)
    }

    @Test
    fun `appendOptional should not add SQL fragment if any param is blank string`() {
        val builder = SqlBuilderImpl()
        builder.appendOptional("AND name = ?", " ")
        assertEquals("", builder.sqlQuery.toString())
        assertEquals(emptyList(), builder.params)
    }
}
