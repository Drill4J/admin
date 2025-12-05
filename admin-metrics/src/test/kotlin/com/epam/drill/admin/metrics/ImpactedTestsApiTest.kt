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
package com.epam.drill.admin.metrics

import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.test.DatabaseTests
import com.epam.drill.admin.test.withTransaction
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.route.payload.TestDetails
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.epam.drill.admin.writer.rawdata.table.CoverageTable
import com.epam.drill.admin.writer.rawdata.table.InstanceTable
import com.epam.drill.admin.writer.rawdata.table.MethodTable
import com.epam.drill.admin.writer.rawdata.table.TestDefinitionTable
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import com.jayway.jsonpath.JsonPath
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.text.get

class ImpactedTestsApiTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {

    @Test
    fun `given a build with changes, impacted tests service should return a list of impacted tests`() =
        havingData {
            build1 has listOf(method1, method2)
            test1 covers method1 on build1
            test2 covers method2 on build1
            build2 hasModified method2 comparedTo build1
        }.expectThat {
            client.getImpactedTests(build2, build1).returns { data ->
                assertTrue { data.isNotEmpty() }
                assertTrue { data.any { it["testName"] == test2.testName } }
                assertTrue { data.none { it["testName"] == test1.testName } }
            }
        }

    @Test
    fun `given page and size, impacted tests service should return tests only for specified page and size`() =
        havingData {
            build1 has listOf(method1)
            for (i in 1..15) {
                val test = TestDetails(testName = "test$i")
                test covers method1 on build1
            }
            build2 hasModified method1 comparedTo build1
        }.expectThat { client ->
            client.getImpactedTests(build2, build1) {
                parameter("page", 1)
                parameter("pageSize", 10)
            }.returns { data ->
                // Should return at most 10 records
                assertTrue(data.size <= 10, "Expected at most 10 records, but got ${data.size}")
            }

            client.getImpactedTests(build2, build1) {
                parameter("page", 2)
                parameter("pageSize", 10)
            }.returns { data ->
                // The second page should contain the remaining tests (5 or fewer)
                assertTrue(data.size <= 5, "Expected at most 5 records, but got ${data.size}")
            }
        }

    @Test
    fun `given test tag filter, impacted tests service should return only tests matching the tag`(): Unit =
        havingData {
            build1 has listOf(method1)
            val taggedTest = TestDetails(testName = "taggedTest", tags = listOf("important-tag"))
            val untaggedTest = TestDetails(testName = "untaggedTest")
            taggedTest covers method1 on build1
            untaggedTest covers method1 on build1
            build2 hasModified method1 comparedTo build1
        }.expectThat { client ->
            client.getImpactedTests(build2, build1) {
                parameter("testTag", "important-tag")
            }.returns { data ->
                assertTrue(data.isNotEmpty(), "Expected at least one test with the specified tag")
                assertTrue(data.all { it["testName"] == "taggedTest" }, "All returned tests should have the specified tag")
                assertTrue(data.none { it["testName"] == "untaggedTest" }, "No untagged tests should be returned")
            }
        }

    @Test
    fun `given test path filter, impacted tests service should return only tests matching the path`() =
        havingData {
            build1 has listOf(method1)
            val test1 = TestDetails(testName = "pathFilteredTest1", path = "com/example/path1/Test1")
            val test2 = TestDetails(testName = "pathFilteredTest2", path = "com/example/path1/Test2")
            val test3 = TestDetails(testName = "differentPathTest", path = "com/example/path2/Test3")
            test1 covers method1 on build1
            test2 covers method1 on build1
            test3 covers method1 on build1
            build2 hasModified method1 comparedTo build1
        }.expectThat { client ->
            client.getImpactedTests(build2, build1) {
                parameter("testPath", "com/example/path1")
            }.returns { data ->
                assertTrue(data.isNotEmpty(), "Expected at least one test with the specified path")
                assertTrue(data.all { it["testName"] == "pathFilteredTest1" || it["testName"] == "pathFilteredTest2" }, "All returned tests should match the specified path")
                assertTrue(data.none { it["testName"] == "differentPathTest" }, "No tests from other paths should be returned")
            }
        }

    @AfterTest
    fun clearAll() = withTransaction {
        CoverageTable.deleteAll()
        InstanceTable.deleteAll()
        MethodTable.deleteAll()
        BuildTable.deleteAll()
        TestLaunchTable.deleteAll()
        TestSessionTable.deleteAll()
        TestDefinitionTable.deleteAll()
    }
}

