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
import com.epam.drill.admin.metrics.config.metricsDIModule
import com.epam.drill.admin.metrics.route.metricsRoutes
import com.epam.drill.admin.test.DatabaseTests
import com.epam.drill.admin.test.drillApplication
import com.epam.drill.admin.test.drillClient
import com.epam.drill.admin.test.withTransaction
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.config.rawDataServicesDIModule
import com.epam.drill.admin.writer.rawdata.route.dataIngestRoutes
import com.epam.drill.admin.writer.rawdata.route.payload.BuildPayload
import com.epam.drill.admin.writer.rawdata.table.*
import com.jayway.jsonpath.JsonPath
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecommendedTestsApiTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {
    @Test
    fun `given test that covers only unmodified methods, recommended test service should suggest skipping it`() {
         havingData {
             build1 has listOf(method1, method2)
             test1 covers method1 with probesOf(1, 0) on build1
             build2 hasModified method2 comparedTo build1
         }.expectThat {
            client.get("/metrics/recommended-tests") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("testsToSkip", true)
                parameter("targetBuildVersion", build2.buildVersion)
            }.returns("$.data.recommendedTests") { recommendedTests ->
                // test1 already checked method1 in build1 and method1 was not modified in build2,
                // that's why test1 should be recommended to skip in build2
                assertEquals(1, recommendedTests.size)
                assertTrue(recommendedTests.any { it["testDefinitionId"] == test1.definitionId })
                assertTrue(recommendedTests.any { it["testImpactStatus"] == "NOT_IMPACTED" })
            }
        }
    }

    @Test
    fun `given test that covers at least one modified method, recommended test service should suggest running it`() {
        havingData {
            build1 has listOf(method1, method2)
            test1 covers method1 with probesOf(1, 0) on build1
            test1 covers method2 with probesOf(1, 0, 0) on build1
            build2 hasModified method2 comparedTo build1
        }.expectThat {
            client.get("/metrics/recommended-tests") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("targetBuildVersion", build2.buildVersion)
                parameter("testsToSkip", false)
            }.returns("$.data.recommendedTests") { recommendedTests ->
                // test1 checked method1 and method2 in build1, but method2 was modified in build2,
                // that's why test1 should be recommended to run in build2
                assertEquals(1, recommendedTests.size)
                assertTrue(recommendedTests.any { it["testDefinitionId"] == test1.definitionId })
                assertTrue(recommendedTests.any { it["testImpactStatus"] == "IMPACTED" })
            }
        }
    }

    @Test
    fun `given test that covers only unmodified methods of at least one build, recommended test service should suggest skipping it`() {
        havingData {
            build1 has listOf(method1, method2)
            test1 covers method1 with probesOf(1, 1) on build1
            build2 hasModified method1 comparedTo build1
            test1 covers method1 with probesOf(1, 1) on build2
            build3 hasNew method3 comparedTo build2
        }.expectThat {
            client.get("/metrics/recommended-tests") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("testsToSkip", true)
                parameter("targetBuildVersion", build3.buildVersion)
            }.returns("$.data.recommendedTests") { recommendedTests ->
                // test1 already checked method1 in build2 and method1 was not modified in build3 compared to build2,
                // that's why test1 should be recommended to skip in build3,
                // despite test1 also checked method1 in build1 and method1 was modified in build2
                assertEquals(1, recommendedTests.size)
                assertTrue(recommendedTests.any { it["testDefinitionId"] == test1.definitionId })
                assertTrue(recommendedTests.any { it["testImpactStatus"] == "NOT_IMPACTED" })
            }
        }
    }

    @Test
    fun `given testTaskId parameter, recommended test service should suggest running tests from only specified test task`() {
        havingData {
            //build1 has 2 methods, test1 and test2 cover method1 and method2
            client.deployInstance(build1, arrayOf(method1, method2))
            client.launchTest(
                session1.testTaskId("check1"), test1, build1, arrayOf(
                    method1 to probesOf(1, 1), method2 to probesOf(1, 1, 1)
                )
            )
            client.launchTest(
                session2.testTaskId("check2"), test2, build1, arrayOf(
                    method1 to probesOf(1, 1), method2 to probesOf(1, 1, 1)
                )
            )
            //build2 has modified method2 compared to build1
            client.deployInstance(build2, arrayOf(method1, method2.changeChecksum()))
        }.expectThat {
            client.get("/metrics/recommended-tests") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("targetBuildVersion", build2.buildVersion)
                parameter("testTaskId", "check2")
                parameter("testsToSkip", false)
            }.returns("$.data.recommendedTests") { recommendedTests ->
                // test1 and test2 should be recommended to run in build2,
                // but only test2 is from the specified test task, that's why it should be marked as IMPACTED,
                // even though test1 is from a different test task, it should still be included in the results but as UNKNOWN_IMPACT
                assertEquals(2, recommendedTests.size)
                assertTrue(recommendedTests.any { it["testDefinitionId"] == test2.definitionId && it["testImpactStatus"] == "IMPACTED" })
                assertTrue(recommendedTests.any { it["testDefinitionId"] == test1.definitionId && it["testImpactStatus"] == "UNKNOWN_IMPACT" })
            }
        }
    }

    @Test
    fun `given baselineBuildBranches parameter, recommended test service should suggest skipping tests if they are not impacted in baselines from specified branch`() {
        havingData {
            client.putBuild(BuildPayload(groupId = build1.groupId, appId = build1.appId, buildVersion = build1.buildVersion, branch = "main"))
            client.putBuild(BuildPayload(groupId = build2.groupId, appId = build2.appId, buildVersion = build2.buildVersion, branch = "feature"))
            //build1 on main branch, test1 covers method2
            client.deployInstance(build1, arrayOf(method1, method2))
            client.launchTest(
                session1, test1, build1, arrayOf(
                    method1 to probesOf(0, 0), method2 to probesOf(1, 1, 1)
                )
            )
            //build2 on feature branch, test1 covers method1
            client.deployInstance(build2, arrayOf(method1, method2))
            client.launchTest(
                session2, test1, build2, arrayOf(
                    method1 to probesOf(1, 1), method2 to probesOf(0, 0, 0)
                )
            )
            //build3 has modified method2 compared to build1 and build2
            client.deployInstance(build3, arrayOf(method1, method2.changeChecksum()))
        }.expectThat {
            client.get("/metrics/recommended-tests") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("targetBuildVersion", build3.buildVersion)
                parameter("baselineBuildBranches", "feature")
                parameter("testsToSkip", true)
            }.returns("$.data.recommendedTests") { recommendedTests ->
                // test1 should be not impacted because in baseline 'build2' from specified branch 'feature' method2 was not impacted by test1
                assertEquals(1, recommendedTests.size)
                assertTrue(recommendedTests.any { it["testDefinitionId"] == test1.definitionId })
                assertTrue(recommendedTests.any { it["testImpactStatus"] == "NOT_IMPACTED" })
            }
        }
    }

    @Test
    fun `given two launches of same test are impacted and not impacted in same build, recommended test service should suggest running it`() {
        havingData {
            client.deployInstance(build1, arrayOf(method1, method2))
            //test1 in session1 covers method2
            client.launchTest(
                session1, test1, build1, arrayOf(
                    method1 to probesOf(0, 0), method2 to probesOf(1, 1, 1)
                )
            )
            //test1 in session2 does not cover method2
            client.launchTest(
                session2, test1, build1, arrayOf(
                    method1 to probesOf(1, 1), method2 to probesOf(0, 0, 0)
                )
            )
            //build2 has modified method2 compared to build1
            client.deployInstance(build2, arrayOf(method1, method2.changeChecksum()))
        }.expectThat {
            client.get("/metrics/recommended-tests") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("targetBuildVersion", build2.buildVersion)
                parameter("testsToSkip", false)
            }.returns("$.data.recommendedTests") { recommendedTests ->
                // test1 should be impacted because shouldn't trust it if the coverage map differs on the same build for the same test.
                assertEquals(1, recommendedTests.size)
                assertTrue(recommendedTests.any { it["testDefinitionId"] == test1.definitionId })
                assertTrue(recommendedTests.any { it["testImpactStatus"] == "IMPACTED" })
            }
        }
    }

    @AfterEach
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
