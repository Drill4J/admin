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
import com.epam.drill.admin.writer.rawdata.table.BuildMethodTable
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.epam.drill.admin.writer.rawdata.table.MethodCoverageTable
import com.epam.drill.admin.writer.rawdata.table.InstanceTable
import com.epam.drill.admin.writer.rawdata.table.MethodTable
import com.epam.drill.admin.writer.rawdata.table.TestDefinitionTable
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import io.ktor.client.request.parameter
import org.jetbrains.exposed.sql.deleteAll

class ImpactedMethodsApiTest : MetricsDatabaseTests({ default, metrics ->
    RawDataWriterDatabaseConfig.init(default)
    MetricsDatabaseConfig.init(metrics)
}) {
    @Test
    fun `given a build with changes, build-changes with hasImpactedTests should return impacted methods`() =
        havingData {
            build1 has listOf(method1, method2)
            test1 covers method1 on build1
            build2 hasModified method1 comparedTo build1
            build2 hasModified method2 comparedTo build1
        }.expectThat { client ->
            client.getBuildChanges(build2, build1, hasImpactedTests = true).returns { data ->
                assertTrue(data.isNotEmpty())
                assertTrue(data.any { it["name"] == method1.name })
                assertTrue(data.none { it["name"] == method2.name })
            }
        }

    @Test
    fun `given page and size, build-changes should paginate impacted methods`() =
        havingData {
            build1 has listOf(method1, method2)
            repeat(15) { i ->
                val test = com.epam.drill.admin.writer.rawdata.route.payload.TestDetails(testName = "test$i")
                test covers method1 on build1
                test covers method2 on build1
            }
            build2 hasModified method1 comparedTo build1
            build2 hasModified method2 comparedTo build1
        }.expectThat { client ->
            client.getBuildChanges(build2, build1, hasImpactedTests = true) {
                parameter("page", 1)
                parameter("pageSize", 10)
            }.returns { data ->
                assertTrue(data.size <= 10, "Expected at most 10 records, but got ${data.size}")
            }

            client.getBuildChanges(build2, build1, hasImpactedTests = true) {
                parameter("page", 2)
                parameter("pageSize", 10)
            }.returns { data ->
                assertTrue(data.isEmpty(), "Expected no records on second page, but got ${data.size}")
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
