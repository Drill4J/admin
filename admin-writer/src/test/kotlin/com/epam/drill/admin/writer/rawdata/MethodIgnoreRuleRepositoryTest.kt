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

import com.epam.drill.admin.test.DatabaseTests
import com.epam.drill.admin.test.withRollback
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.entity.MethodIgnoreRule
import com.epam.drill.admin.writer.rawdata.repository.impl.MethodIgnoreRuleRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MethodIgnoreRuleRepositoryTest : DatabaseTests({ RawDataWriterDatabaseConfig.init(it) }) {
    private val repository = MethodIgnoreRuleRepositoryImpl()

    @Test
    fun `rules are listed and deleted within group and app scope`() = withRollback {
        repository.create(
            MethodIgnoreRule(
                groupId = "group",
                appId = "app",
                classnamePattern = "sample/.*",
                namePattern = "get.*",
            )
        )
        repository.create(
            MethodIgnoreRule(
                groupId = "group",
                appId = "other-app",
                namePattern = "set.*",
            )
        )

        val appRules = repository.getAll("group", "app", page = 1, pageSize = 20)
        assertEquals(1, appRules.total)
        assertEquals(1, appRules.data.size)
        assertEquals("get.*", appRules.data.single().namePattern)

        repository.deleteById("group", "other-app", appRules.data.single().id)
        assertEquals(1, repository.getAll("group", "app", page = 1, pageSize = 20).total)

        repository.deleteById("group", "app", appRules.data.single().id)
        assertEquals(0, repository.getAll("group", "app", page = 1, pageSize = 20).total)
    }

    @Test
    fun `syncAll replaces existing rules for the given scope`() = withRollback {
        repository.create(MethodIgnoreRule(groupId = "group", appId = "app", namePattern = "old.*"))
        repository.create(MethodIgnoreRule(groupId = "group", appId = "app", classnamePattern = "com\\.old\\..*"))
        repository.create(MethodIgnoreRule(groupId = "group", appId = "other-app", namePattern = "unrelated.*"))

        assertEquals(2, repository.getAll("group", "app", 1, 20).total)

        repository.syncAll(
            groupId = "group",
            appId = "app",
            rules = listOf(
                MethodIgnoreRule(groupId = "group", appId = "app", namePattern = "new.*", classnamePattern = "com\\.new\\..*"),
            )
        )

        val result = repository.getAll("group", "app", 1, 20)
        assertEquals(1, result.total)
        assertEquals("new.*", result.data.single().namePattern)
        assertEquals("com\\.new\\..*", result.data.single().classnamePattern)

        assertEquals(1, repository.getAll("group", "other-app", 1, 20).total, "other-app rules must be untouched")
    }

    @Test
    fun `getList returns all rules only for the given group and app`() = withRollback {
        repository.create(MethodIgnoreRule(groupId = "group", appId = "app", namePattern = "get.*", classnamePattern = "sample/.*"))
        repository.create(MethodIgnoreRule(groupId = "group", appId = "app", namePattern = "set.*"))
        repository.create(MethodIgnoreRule(groupId = "group", appId = "other-app", namePattern = "other.*"))
        repository.create(MethodIgnoreRule(groupId = "other-group", appId = "app", namePattern = "foreign.*"))

        val rules = repository.getList("group", "app")

        assertEquals(2, rules.size)
        assertEquals(setOf("get.*", "set.*"), rules.map { it.namePattern }.toSet())
        assertEquals("sample/.*", rules.single { it.namePattern == "get.*" }.classnamePattern)
        assertTrue(rules.all { it.groupId == "group" && it.appId == "app" })
    }

    @Test
    fun `syncAll with empty list clears all rules for the scope`() = withRollback {
        repository.create(MethodIgnoreRule(groupId = "group", appId = "app", namePattern = "get.*"))
        repository.create(MethodIgnoreRule(groupId = "group", appId = "other-app", namePattern = "set.*"))

        repository.syncAll(groupId = "group", appId = "app", rules = emptyList())

        assertEquals(0, repository.getAll("group", "app", 1, 20).total)
        assertEquals(1, repository.getAll("group", "other-app", 1, 20).total, "other-app rules must be untouched")
    }
}
