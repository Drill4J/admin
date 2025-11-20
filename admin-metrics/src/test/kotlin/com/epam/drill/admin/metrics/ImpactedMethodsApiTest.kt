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

class ImpactedMethodsApiTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {
    @Test
    fun `given a build with changes, impacted methods service should return a list of impacted methods`() =
        havingData {
            build1 has listOf(method1, method2)
            test1 covers method1 on build1
            build2 hasModified method1 comparedTo build1
            build2 hasModified method2 comparedTo build1
        }.expectThat { client ->
             client.getImpactedMethods(build2, build1).returns { data ->
                assertTrue(data.isNotEmpty())
                assertTrue(data.any { it["name"] == method1.name })
                assertTrue(data.none { it["name"] == method2.name })
            }
        }

    @Test
    fun `given page and size, impacted methods service should return methods only for specified page and size`() =
        havingData {
            build1 has listOf(method1, method2)
            // Simulate multiple test launches to generate enough impacted methods for pagination
            repeat(15) { i ->
                val test = TestDetails(testName = "test$i")
                test covers method1 on build1
                test covers method2 on build1
            }
            build2 hasModified method1 comparedTo build1
            build2 hasModified method2 comparedTo build1
        }.expectThat { client ->
            client.getImpactedMethods(build2, build1) {
                parameter("page", 1)
                parameter("pageSize", 10)
            }.returns { data ->
                // Should return at most 10 records
                assertTrue(data.size <= 10, "Expected at most 10 records, but got ${data.size}")
            }

            client.getImpactedMethods(build2, build1) {
                parameter("page", 2)
                parameter("pageSize", 10)
            }.returns { data ->
                // The second page should contain the remaining tests (5 or fewer)
                assertTrue(data.size <= 5, "Expected at most 5 records, but got ${data.size}")
            }
        }

    @Test
    fun `given test filter, impacted methods service should return only methods matching path and name`() =
        havingData {
            build1 has listOf(method1, method2)
            val test1Details = TestDetails(testName = "test1", path = "com/example/path1/Test1")
            val test2Details = TestDetails(testName = "test2", path = "com/example/path1/Test2")
            val test3Details = TestDetails(testName = "test2", path = "com/example/other_path/Test3")
            test1Details covers method1 on build1
            test2Details covers method2 on build1
            test3Details covers method1 on build1
            test3Details covers method2 on build1
            build2 hasModified method1 comparedTo build1
            build2 hasModified method2 comparedTo build1
        }.expectThat { client ->
            client.getImpactedMethods(build2, build1) {
                parameter("testPath", "com/example/path1")
                parameter("testName", "test2")
            }.returns { data ->
                assertTrue(data.isNotEmpty())
                assertTrue(data.any { it["name"] == method2.name })
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

