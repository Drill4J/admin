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
import com.epam.drill.admin.metrics.views.TestImpactStatus
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
import org.jetbrains.exposed.sql.deleteAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestImpactAnalysisTest : MetricsDatabaseTests({ default, metrics ->
    RawDataWriterDatabaseConfig.init(default)
    MetricsDatabaseConfig.init(metrics)
}) {

    @Test
    fun `given modified methods compared to baseline, impacted tests service should return tests which cover modified methods`() =
        havingData {
            build1 has listOf(method1)
            test1 covers method1 on build1
            build2 hasModified method1 comparedTo build1
        }.expectThat {
            test1 isImpactedOn build2 comparedTo build1
            method1 isImpactedOn build2 comparedTo build1
            //because
            method1 isModifiedOn build2 comparedTo build1
        }

    @Test
    fun `given equal methods compared to baseline, impacted tests service should not return tests which cover equal methods`() =
        havingData {
            build1 has listOf(method1)
            test1 covers method1 on build1
            build2 hasModified method1 comparedTo build1
            test1 covers method1 on build2
            build3 hasTheSameMethodsComparedTo build2
        }.expectThat {
            test1 isNotImpactedOn build3 comparedTo build2
            method1 isNotImpactedOn build3 comparedTo build2
            //because
            method1 isEqualOn build3 comparedTo build2
        }

    @Test
    fun `given deleted methods compared to baseline, impacted tests service should return tests witch cover deleted methods`() =
        havingData {
            build1 has listOf(method1, method2)
            test1 covers method1 on build1
            build2 hasDeleted method1 comparedTo build1
        }.expectThat {
            test1 isImpactedOn build2 comparedTo build1
            method1 isImpactedOn build2 comparedTo build1
            //because
            method1 isDeletedOn build2 comparedTo build1
        }

    @Test
    fun `given new covered methods compared to baseline, impacted tests service should return tests which cover new methods`() =
        havingData {
            build1 has listOf(method1)
            build2 hasNew method2 comparedTo build1
            test1 covers method2 on build2
        }.expectThat {
            test1 isImpactedOn build2 comparedTo build1

            method2 isImpactedOn build2 comparedTo build1
            //because
            method2 isNewOn build2 comparedTo build1
            method2 isCoveredOn build2
        }

    @Test
    fun `given new uncovered methods compared to baseline, impacted methods service should not return uncovered new methods`() =
        havingData {
            build1 has listOf(method1)
            build2 hasNew method2 comparedTo build1
        }.expectThat {
            method2 isNotImpactedOn build2 comparedTo build1
            //because
            method2 isNewOn build2 comparedTo build1
            method2 isNotCoveredOn build2
        }

    @Test
    fun `given failed test, impacted tests service should return unknown impact`() =
        havingData {
            build1 has listOf(method1)
            test1 failsOn method1 on build1
            build2 hasModified method1 comparedTo build1
        }.expectThat {
            test1 hasUnknownImpactOn build2 comparedTo build1
        }

    @Test
    fun `given all impact statuses requested, impacted tests service should return each test with correct status`() =
        havingData {
            build1 has listOf(method1, method2)
            val impactedTest = TestDetails(testName = "impactedTest")
            val notImpactedTest = TestDetails(testName = "notImpactedTest")
            val unknownImpactTest = TestDetails(testName = "unknownImpactTest")
            impactedTest covers method1 on build1
            notImpactedTest covers method2 on build1
            unknownImpactTest failsOn method1 on build1
            build2 hasModified method1 comparedTo build1
        }.expectThat { client ->
            client.postImpactedTests(build2, build1) {
                put(
                    "impactStatuses",
                    listOf(
                        TestImpactStatus.IMPACTED.name,
                        TestImpactStatus.NOT_IMPACTED.name,
                        TestImpactStatus.UNKNOWN_IMPACT.name,
                    )
                )
                put("pageSize", 100)
            }.returns { data ->
                assertEquals(
                    3,
                    data.size,
                    "Expected exactly 3 tests (one per impact status), but got ${data.size}: ${data.map { it["testName"] }}"
                )
                val impactedTest = TestDetails(testName = "impactedTest")
                val notImpactedTest = TestDetails(testName = "notImpactedTest")
                val unknownImpactTest = TestDetails(testName = "unknownImpactTest")
                impactedTest.assertTestIsImpacted(data)
                notImpactedTest.assertTestIsNotImpacted(data)
                unknownImpactTest.assertTestHasUnknownImpact(data)
            }
        }

    @Test
    fun `given IMPACTED filter only, impacted tests service should not return not impacted or unknown tests`() =
        havingData {
            build1 has listOf(method1, method2)
            val impactedTest = TestDetails(testName = "impactedTest")
            val notImpactedTest = TestDetails(testName = "notImpactedTest")
            val unknownImpactTest = TestDetails(testName = "unknownImpactTest")
            impactedTest covers method1 on build1
            notImpactedTest covers method2 on build1
            unknownImpactTest failsOn method1 on build1
            build2 hasModified method1 comparedTo build1
        }.expectThat { client ->
            client.postImpactedTests(build2, build1, TestImpactStatus.IMPACTED).returns { data ->
                TestDetails(testName = "impactedTest").assertTestIsImpacted(data)
                TestDetails(testName = "notImpactedTest").assertTestIsAbsent(data)
                TestDetails(testName = "unknownImpactTest").assertTestIsAbsent(data)
            }
        }

    @Test
    fun `given NOT_IMPACTED filter only, impacted tests service should not return impacted or unknown tests`() =
        havingData {
            build1 has listOf(method1, method2)
            val impactedTest = TestDetails(testName = "impactedTest")
            val notImpactedTest = TestDetails(testName = "notImpactedTest")
            val unknownImpactTest = TestDetails(testName = "unknownImpactTest")
            impactedTest covers method1 on build1
            notImpactedTest covers method2 on build1
            unknownImpactTest failsOn method1 on build1
            build2 hasModified method1 comparedTo build1
        }.expectThat { client ->
            client.postImpactedTests(build2, build1, TestImpactStatus.NOT_IMPACTED).returns { data ->
                TestDetails(testName = "notImpactedTest").assertTestIsNotImpacted(data)
                TestDetails(testName = "impactedTest").assertTestIsAbsent(data)
                TestDetails(testName = "unknownImpactTest").assertTestIsAbsent(data)
            }
        }

    @Test
    fun `given method signature filter, impacted tests service should select matching tests but count all changed methods`() =
        havingData {
            build1 has listOf(method1, method2)
            val testCoveringBoth = TestDetails(testName = "testCoveringBoth")
            val testCoveringMethod2Only = TestDetails(testName = "testCoveringMethod2Only")
            testCoveringBoth covers method1 on build1
            testCoveringBoth covers method2 on build1
            testCoveringMethod2Only covers method2 on build1
            build2 hasModified method1 comparedTo build1
            build2 hasModified method2 comparedTo build1
        }.expectThat { client ->
            client.postImpactedTests(build2, build1) {
                put("methodName", "method1")
                put("pageSize", 100)
            }.returns { data ->
                val testCoveringBoth = TestDetails(testName = "testCoveringBoth")
                val testCoveringMethod2Only = TestDetails(testName = "testCoveringMethod2Only")
                testCoveringBoth.assertTestIsImpacted(data)
                testCoveringBoth.assertImpactedMethodsEquals(data, 2)
                testCoveringMethod2Only.assertTestIsAbsent(data)
            }
        }

    @Test
    fun `given tests covering non-existent methods on both target and baseline builds, impacted tests service should not return these tests`() =
        havingData {
            build1 has listOf(method1)
            build2 hasNew method2 comparedTo build1
            test1 covers method2 on build2
            build3 hasDeleted method2 comparedTo build2
        }.expectThat {
            test1 isNotImpactedOn build3 comparedTo build1
            method2 isNotImpactedOn build3 comparedTo build1
            //because method2 does not exist on both build3 and build1
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

