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
import com.epam.drill.admin.test.MetricsDatabaseTests
import com.epam.drill.admin.test.withTransaction
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.route.payload.TestDetails
import com.epam.drill.admin.writer.rawdata.table.BuildMethodTable
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.epam.drill.admin.writer.rawdata.table.MethodCoverageTable
import com.epam.drill.admin.writer.rawdata.table.InstanceTable
import com.epam.drill.admin.writer.rawdata.table.MethodTable
import com.epam.drill.admin.writer.rawdata.table.TestDefinitionTable
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.deleteAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImpactedTestsApiTest : MetricsDatabaseTests({ default, metrics ->
    RawDataWriterDatabaseConfig.init(default)
    MetricsDatabaseConfig.init(metrics)
}) {

    @Test
    fun `given a build with changes, impacted tests service should return a list of impacted tests`() =
        havingData {
            build1 has listOf(method1, method2)
            test1 covers method1 on build1
            test2 covers method2 on build1
            build2 hasModified method2 comparedTo build1
        }.expectThat {
            client.postImpactedTests(build2, build1).returns { data ->
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
            client.postImpactedTests(build2, build1) {
                put("page", 1)
                put("pageSize", 10)
            }.returns { data ->
                // Should return at most 10 records
                assertTrue(data.size <= 10, "Expected at most 10 records, but got ${data.size}")
            }

            client.postImpactedTests(build2, build1) {
                put("page", 2)
                put("pageSize", 10)
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
            client.postImpactedTests(build2, build1) {
                put("testTag", "important-tag")
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
            client.postImpactedTests(build2, build1) {
                put("testPath", "com/example/path1")
            }.returns { data ->
                assertTrue(data.isNotEmpty(), "Expected at least one test with the specified path")
                assertTrue(data.all { it["testName"] == "pathFilteredTest1" || it["testName"] == "pathFilteredTest2" }, "All returned tests should match the specified path")
                assertTrue(data.none { it["testName"] == "differentPathTest" }, "No tests from other paths should be returned")
            }
        }


    @Test
    fun `given sortBy impactedMethods and sortOrder DESC, impacted tests should be sorted by number of impacted methods descending`() =
        havingData {
            build1 has listOf(method1, method2, method3)
            val test1 = TestDetails(testName = "testWith1Method")
            val test2 = TestDetails(testName = "testWith2Methods")
            val test3 = TestDetails(testName = "testWith3Methods")

            // test1 covers only method1
            test1 covers method1 on build1

            // test2 covers method1 and method2
            test2 covers method1 on build1
            test2 covers method2 on build1

            // test3 covers all three methods
            test3 covers method1 on build1
            test3 covers method2 on build1
            test3 covers method3 on build1

            // Modify all methods so all tests are impacted
            build2 hasModified method1 comparedTo build1
            build2 hasModified method2 comparedTo build1
            build2 hasModified method3 comparedTo build1
        }.expectThat { client ->
            client.postImpactedTests(build2, build1) {
                put("sortBy", "impactedMethods")
                put("sortOrder", "DESC")
            }.returns { data ->
                assertTrue(data.size >= 3, "Expected at least 3 tests")
                val sortedByImpactedMethods = data.sortedByDescending { (it["impactedMethods"] as Number?)?.toInt() ?: 0 }
                val actualTestNames = data.map { it["testName"] as String }
                val expectedTestNames = sortedByImpactedMethods.map { it["testName"] as String }
                assertTrue(actualTestNames == expectedTestNames,
                    "Tests should be sorted by impactedMethods DESC. Expected: $expectedTestNames, but got: $actualTestNames")
            }
        }

    @Test
    fun `given invalid sortBy, should return BadRequest`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 covers method1 on build1
            build2 hasModified method1 comparedTo build1
        }.expectThat { client ->
            val response = client.post("/metrics/impacted-tests") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("groupId", build2.groupId)
                        put("appId", build2.appId)
                        put("buildVersion", build2.buildVersion)
                        put("baselineBuildVersion", build1.buildVersion)
                        put("sortBy", "invalidSortBy")
                    }
                )
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Invalid sortBy"))
        }

    @Test
    fun `given excludeMethodSignatures, impacted tests should exclude tests that only cover excluded methods`() =
        havingData {
            build1 has listOf(method1, method2, method3)
            val testCoveringMethod1 = TestDetails(testName = "testCoveringMethod1")
            val testCoveringMethod2 = TestDetails(testName = "testCoveringMethod2")
            val testCoveringBoth = TestDetails(testName = "testCoveringBoth")

            // testCoveringMethod1 covers only method1
            testCoveringMethod1 covers method1 on build1

            // testCoveringMethod2 covers only method2
            testCoveringMethod2 covers method2 on build1

            // testCoveringBoth covers both method1 and method2
            testCoveringBoth covers method1 on build1
            testCoveringBoth covers method2 on build1

            // Modify all methods
            build2 hasModified method1 comparedTo build1
            build2 hasModified method2 comparedTo build1
        }.expectThat { client ->
            // Exclude method1 signature: className:methodName:params:returnType
            val method1Signature = "${method1.classname}:${method1.name}:${method1.params}:${method1.returnType}"

            client.postImpactedTests(build2, build1) {
                put("excludeMethodSignatures", listOf(method1Signature))
            }.returns { data ->
                assertTrue(data.isNotEmpty(), "Expected some tests to remain after exclusion")

                // testCoveringMethod1 should NOT be in results (only covers excluded method1)
                assertTrue(data.none { it["testName"] == "testCoveringMethod1" },
                    "testCoveringMethod1 should be excluded as it only covers method1")

                // testCoveringMethod2 should be in results (covers non-excluded method2)
                assertTrue(data.any { it["testName"] == "testCoveringMethod2" },
                    "testCoveringMethod2 should be included as it covers non-excluded method2")

                // testCoveringBoth should be in results (covers not only excluded method1)
                assertTrue(data.any { it["testName"] == "testCoveringBoth" },
                    "testCoveringBoth should be included as it covers not only method1")
            }
        }

    @Test
    fun `given test task id filter, impacted tests service should return only tests from matching task`() =
        havingData {
            build1 has listOf(method1)
            val taskASession = session1.testTaskId("task-a")
            val taskBSession = session2.testTaskId("task-b")
            val testFromTaskA = TestDetails(testName = "testFromTaskA")
            val testFromTaskB = TestDetails(testName = "testFromTaskB")
            testFromTaskA of taskASession covers method1 on build1
            testFromTaskB of taskBSession covers method1 on build1
            build2 hasModified method1 comparedTo build1
        }.expectThat { client ->
            client.postImpactedTests(build2, build1) {
                put("testTaskId", "task-a")
            }.returns { data ->
                assertTrue(data.isNotEmpty(), "Expected at least one test from task-a")
                assertTrue(data.all { it["testName"] == "testFromTaskA" }, "All returned tests should belong to task-a")
                assertTrue(data.all { it["testTaskId"] == "task-a" }, "All returned tests should have testTaskId task-a")
                assertTrue(data.none { it["testName"] == "testFromTaskB" }, "No tests from task-b should be returned")
            }
        }

    @Test
    fun `given impacted tests, filter-options should return available test task ids`() =
        havingData {
            build1 has listOf(method1)
            val taskASession = session1.testTaskId("task-a")
            val taskBSession = session2.testTaskId("task-b")
            val testFromTaskA = TestDetails(testName = "testFromTaskA")
            val testFromTaskB = TestDetails(testName = "testFromTaskB")
            testFromTaskA of taskASession covers method1 on build1
            testFromTaskB of taskBSession covers method1 on build1
            build2 hasModified method1 comparedTo build1
        }.expectThat { client ->
            client.postImpactedTestsFilterOptions(build2, build1).returnsSingle { options ->
                val testTaskIds = options["testTaskIds"] as? List<*>
                assertEquals(listOf("task-a", "task-b"), testTaskIds)
            }
        }

    @Test
    fun `given test task id filter, impacted methods service should count only tests from matching task`() =
        havingData {
            build1 has listOf(method1)
            val taskASession = session1.testTaskId("task-a")
            val taskBSession = session2.testTaskId("task-b")
            val testFromTaskA = TestDetails(testName = "testFromTaskA")
            val testFromTaskB = TestDetails(testName = "testFromTaskB")
            testFromTaskA of taskASession covers method1 on build1
            testFromTaskB of taskBSession covers method1 on build1
            build2 hasModified method1 comparedTo build1
        }.expectThat { client ->
            client.getImpactedMethods(build2, build1).returns { data ->
                val methodRow = data.find { it["name"] == method1.name }
                assertEquals(2, (methodRow?.get("impactedTests") as Number?)?.toInt())
            }

            client.getImpactedMethods(build2, build1) {
                parameter("testTaskId", "task-a")
            }.returns { data ->
                val methodRow = data.find { it["name"] == method1.name }
                assertEquals(1, (methodRow?.get("impactedTests") as Number?)?.toInt())
            }
        }

    @AfterTest
    fun clearAll() = withTransaction(RawDataWriterDatabaseConfig.database) {
        MethodCoverageTable.deleteAll()
        InstanceTable.deleteAll()
        MethodTable.deleteAll()
        BuildMethodTable.deleteAll()
        BuildTable.deleteAll()
        TestLaunchTable.deleteAll()
        TestSessionTable.deleteAll()
        TestDefinitionTable.deleteAll()
    }
}

