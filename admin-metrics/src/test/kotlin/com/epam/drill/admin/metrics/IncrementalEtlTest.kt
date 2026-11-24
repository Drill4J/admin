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
import com.epam.drill.admin.metrics.config.executeQueryReturnMap
import com.epam.drill.admin.writer.rawdata.route.payload.*
import com.epam.drill.admin.writer.rawdata.util.combineChecksumsCrc64
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Checks that incremental ETL picks up raw data whose `updated_at` is touched by RawDataWriter
 * when related data arrives later.
 */
class IncrementalEtlTest : MetricsApiTests() {

    @Test
    fun `given coverage sent before its test launch, incremental ETL should load it once the test launch arrives`() {
        val testLaunchId = "late-test-launch"
        havingData {
            build1 has listOf(method1)
        }.whenExecuting {
            putTestSession(session1)
            postTestDefinitions(test1.toDefinitionsPayload(session1))
            postCoverage(build1.coverageOf(method1, testLaunchId = testLaunchId, testSessionId = session1.id))
            syncMetrics(testGroup)
            assertEquals(0, countRows("metrics.build_method_coverage", build1.buildId))
            assertEquals(0, countRows("metrics.test_to_code_mapping"))
        }.whenExecuting {
            postTestLaunches(test1.toLaunchesPayload(session1, testLaunchId))
        }.expectThat(onFailed = { syncMetrics(testGroup) }) {
            method1 isCoveredOn build1
            assertTrue(countRows("metrics.build_method_coverage", build1.buildId) > 0)
            assertTrue(countRows("metrics.build_coverage", build1.buildId) > 0)
            assertTrue(countRows("metrics.test_to_code_mapping") > 0)
        }
    }

    @Test
    fun `given coverage sent before build is finalized, incremental ETL should load it once the build becomes valid`() {
        havingData {
        }.whenExecuting {
            putInstance(build1)
            putMethods(build1.methodsPayloadOf(method1))
            postCoverage(build1.coverageOf(method1))
            syncMetrics(testGroup)
            assertEquals(0, countRows("metrics.build_method_coverage", build1.buildId))
            assertEquals(0, countRows("metrics.build_coverage", build1.buildId))
        }.whenExecuting {
            finalizeBuild(build1.finalizePayloadOf(method1))
        }.expectThat(onFailed = { syncMetrics(testGroup) }) {
            method1 isCoveredOn build1
            assertTrue(countRows("metrics.build_method_coverage", build1.buildId) > 0)
            assertTrue(countRows("metrics.build_coverage", build1.buildId) > 0)
        }
    }

    @Test
    fun `given instance heartbeat, incremental ETL should update the build`() {
        lateinit var initialHeartbeatAt: LocalDateTime
        havingData {
            build1 has listOf(method1)
        }.whenExecuting {
            initialHeartbeatAt = assertNotNull(buildRow(build1.buildId)["last_instance_heartbeat_at"] as LocalDateTime?)
            putInstanceHeartbeat(
                InstanceHeartbeatPayload(
                    groupId = build1.groupId,
                    appId = build1.appId,
                    instanceId = build1.instanceId,
                    commitSha = build1.commitSha,
                    buildVersion = build1.buildVersion,
                    status = AgentHeartbeatStatus.RUNNING,
                )
            )
            syncMetrics(testGroup)
        }.expectThat(onFailed = { syncMetrics(testGroup) }) {
            val heartbeatAt = buildRow(build1.buildId)["last_instance_heartbeat_at"] as LocalDateTime
            assertTrue(heartbeatAt.isAfter(initialHeartbeatAt), "Expected $heartbeatAt to be after $initialHeartbeatAt")
        }
    }

    @Test
    fun `given test session heartbeat, incremental ETL should update the test session`() {
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 on build1
        }.whenExecuting {
            assertEquals(TestSessionStatus.RUNNING.name, testSessionRow(session1.id)["session_status"])
            putTestSessionHeartbeat(
                TestSessionHeartbeatPayload(
                    groupId = session1.groupId,
                    testProjectId = session1.testProjectId ?: "",
                    testSessionId = session1.id,
                    status = TestSessionStatus.FINISHED,
                )
            )
        }.expectThat(onFailed = { syncMetrics(testGroup) }) {
            assertEquals(TestSessionStatus.FINISHED.name, testSessionRow(session1.id)["session_status"])
        }
    }

    private fun InstancePayload.coverageOf(
        method: SingleMethodPayload,
        testLaunchId: String? = null,
        testSessionId: String? = null,
    ) = CoveragePayload(
        groupId = groupId,
        appId = appId,
        instanceId = instanceId,
        buildVersion = buildVersion,
        commitSha = commitSha,
        coverage = listOf(
            SingleMethodCoveragePayload(
                signature = method.signature,
                bodyChecksum = method.bodyChecksum,
                testId = testLaunchId,
                testSessionId = testSessionId,
                probes = null,
                stringProbes = "1".repeat(method.probesCount),
            )
        )
    )

    private fun InstancePayload.methodsPayloadOf(vararg methods: SingleMethodPayload) = MethodsPayload(
        groupId = groupId,
        appId = appId,
        buildVersion = buildVersion,
        methods = arrayOf(*methods),
    )

    private fun InstancePayload.finalizePayloadOf(vararg methods: SingleMethodPayload) = BuildFinalizePayload(
        groupId = groupId,
        appId = appId,
        buildVersion = buildVersion,
        methodsCount = methods.size,
        methodsChecksum = combineChecksumsCrc64(methods.map { it.bodyChecksum }),
    )

    private fun TestDetails.toDefinitionsPayload(session: TestSessionPayload) = AddTestDefinitionsPayload(
        groupId = session.groupId,
        testProjectId = session.testProjectId,
        definitions = listOf(
            TestDefinitionPayload(
                id = definitionId,
                runner = runner,
                path = path,
                name = testName,
                tags = tags,
                metadata = metadata,
            )
        )
    )

    private fun TestDetails.toLaunchesPayload(session: TestSessionPayload, testLaunchId: String) = AddTestLaunchesPayload(
        groupId = session.groupId,
        testSessionId = session.id,
        testProjectId = session.testProjectId,
        launches = listOf(
            TestLaunchPayload(
                id = testLaunchId,
                testDefinitionId = definitionId,
                result = TestResult.PASSED.name,
                duration = 10,
            )
        )
    )

    private suspend fun countRows(table: String, buildId: String? = null): Int = MetricsDatabaseConfig.transaction {
        executeQueryReturnMap {
            append("SELECT 1 FROM $table")
            append(" WHERE group_id = ?", testGroup)
            appendOptional(" AND build_id = ?", buildId)
        }.size
    }

    private suspend fun buildRow(buildId: String): Map<String, Any?> = MetricsDatabaseConfig.transaction {
        executeQueryReturnMap(
            "SELECT * FROM metrics.builds WHERE group_id = ? AND build_id = ?", testGroup, buildId
        ).single()
    }

    private suspend fun testSessionRow(testSessionId: String): Map<String, Any?> = MetricsDatabaseConfig.transaction {
        executeQueryReturnMap(
            "SELECT * FROM metrics.test_sessions WHERE group_id = ? AND test_session_id = ?", testGroup, testSessionId
        ).single()
    }
}
