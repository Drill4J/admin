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
import com.epam.drill.admin.test.MetricsDatabaseTests
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.route.payload.InstancePayload
import com.epam.drill.admin.writer.rawdata.route.payload.SessionPayload
import io.ktor.client.request.*
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class DataDeletionApiTest : MetricsDatabaseTests({ default, metrics ->
    MetricsDatabaseConfig.init(metrics)
    RawDataWriterDatabaseConfig.init(default)
}) {

    @Test
    fun `delete group should remove all metrics for the group`() {
        val delGroupId = "group-del"
        val keepGroupId = "group-keep"
        val delBuild =
            InstancePayload(groupId = delGroupId, appId = "app-1", instanceId = "inst-1", buildVersion = "1.0.0")
        val keepBuild =
            InstancePayload(groupId = keepGroupId, appId = "app-1", instanceId = "inst-1", buildVersion = "1.0.0")
        val delSession =
            SessionPayload(
                groupId = delGroupId,
                id = "sess1",
                testTaskId = "task",
                startedAt = Clock.System.now()
            )
        val keepSession = SessionPayload(
            groupId = keepGroupId,
            id = "sess2",
            testTaskId = "task",
            startedAt = Clock.System.now()
        )

        havingData {
            delBuild has listOf(method1)
            keepBuild has listOf(method1)
            test1 of delSession covers method1 on delBuild
            test1 of keepSession covers method1 on keepBuild
        }.afterCalling {
            delete("/data-management/groups/$delGroupId").assertSuccessStatus()
        }.expectThat {
            assertThatTableHasNot("metrics.builds", delGroupId)
            assertThatTableHasNot("metrics.methods", delGroupId)
            assertThatTableHasNot("metrics.test_sessions", delGroupId)
            assertThatTableHasNot("metrics.test_launches", delGroupId)
            assertThatTableHasNot("metrics.test_definitions", delGroupId)
            assertThatTableHasNot("metrics.method_daily_coverage", delGroupId)
            assertThatTableHasNot("metrics.test_to_code_mapping", delGroupId)

            assertThatTableHas("metrics.builds", keepGroupId)
            assertThatTableHas("metrics.methods", keepGroupId)
            assertThatTableHas("metrics.test_sessions", keepGroupId)
            assertThatTableHas("metrics.test_launches", keepGroupId)
            assertThatTableHas("metrics.test_definitions", keepGroupId)
            assertThatTableHas("metrics.method_daily_coverage", keepGroupId)
            assertThatTableHas("metrics.test_to_code_mapping", keepGroupId)
        }
    }


    @Test
    fun `delete app should remove all metrics for the app`() {
        val groupId = testGroup
        val delAppId = "app-del"
        val keepAppId = "app-keep"
        val delBuild =
            InstancePayload(groupId = groupId, appId = delAppId, instanceId = "inst-1", buildVersion = "1.0.0")
        val keepBuild =
            InstancePayload(groupId = groupId, appId = keepAppId, instanceId = "inst-1", buildVersion = "1.0.0")
        val session1 = SessionPayload(
            groupId = groupId,
            id = "sess1",
            testTaskId = "task",
            startedAt = Clock.System.now()
        )
        val session2 = SessionPayload(
            groupId = groupId,
            id = "sess2",
            testTaskId = "task",
            startedAt = Clock.System.now()
        )

        havingData {
            delBuild has listOf(method1)
            keepBuild has listOf(method2)
            (test1 of session1) covers method1 on delBuild
            (test2 of session2) covers method2 on keepBuild
        }.afterCalling {
            delete("/data-management/groups/$groupId/apps/$delAppId").assertSuccessStatus()
        }.expectThat {
            assertThatTableHasNot("metrics.builds", groupId, appId = delAppId)
            assertThatTableHasNot("metrics.methods", groupId, appId = delAppId)
            assertThatTableHasNot("metrics.method_daily_coverage", groupId, appId = delAppId)
            assertThatTableHasNot("metrics.test_to_code_mapping", groupId, appId = delAppId)

            assertThatTableHas("metrics.builds", groupId, appId = keepAppId)
            assertThatTableHas("metrics.methods", groupId, appId = keepAppId)
            assertThatTableHas("metrics.method_daily_coverage", groupId, appId = keepAppId)
            assertThatTableHas("metrics.test_to_code_mapping", groupId, appId = keepAppId)
        }
    }

    @Test
    fun `delete test project should remove all metrics for the test project`() {
        val groupId = testGroup
        val delTestProjectId = "tp-del"
        val keepTestProjectId = "tp-keep"
        val build = InstancePayload(groupId = groupId, appId = "app-1", instanceId = "inst-1", buildVersion = "1.0.0")
        val delSession = SessionPayload(
            groupId = groupId, id = "sess1", testTaskId = "task",
            startedAt = Clock.System.now(), testProjectId = delTestProjectId
        )
        val keepSession = SessionPayload(
            groupId = groupId, id = "sess2", testTaskId = "task",
            startedAt = Clock.System.now(), testProjectId = keepTestProjectId
        )

        havingData {
            build has listOf(method1)
            (test1 of delSession) covers method1 on build
            (test2 of keepSession) covers method1 on build
        }.afterCalling {
            delete("/data-management/groups/$groupId/tests/$delTestProjectId").assertSuccessStatus()
        }.expectThat {
            assertThatTableHasNot("metrics.test_sessions", groupId, testProjectId = delTestProjectId)
            assertThatTableHasNot("metrics.test_launches", groupId, testProjectId = delTestProjectId)
            assertThatTableHasNot("metrics.test_definitions", groupId, testProjectId = delTestProjectId)
            assertThatTableHasNot("metrics.method_daily_coverage", groupId, testProjectId = delTestProjectId)
            assertThatTableHasNot("metrics.test_to_code_mapping", groupId, testProjectId = delTestProjectId)

            assertThatTableHas("metrics.test_sessions", groupId, testProjectId = keepTestProjectId)
            assertThatTableHas("metrics.test_launches", groupId, testProjectId = keepTestProjectId)
            assertThatTableHas("metrics.test_definitions", groupId, testProjectId = keepTestProjectId)
            assertThatTableHas("metrics.method_daily_coverage", groupId, testProjectId = keepTestProjectId)
            assertThatTableHas("metrics.test_to_code_mapping", groupId, testProjectId = keepTestProjectId)
        }
    }

    @Test
    fun `delete build should remove all raw data and metrics for the build`() {
        val delBuild = build1
        val keepBuild = build2
        val groupId = testGroup
        val appId = testApp
        val delBuildId = delBuild.buildId
        val keepBuildId = keepBuild.buildId
        havingData {
            delBuild has listOf(method1)
            keepBuild has listOf(method1, method2)
            test1 covers method1 on delBuild
            test2 covers method2 on keepBuild
        }.afterCalling {
            delete("/data-management/groups/$groupId/apps/$appId/builds/$delBuildId").assertSuccessStatus()
        }.expectThat {
            assertThatTableHasNot("metrics.builds", groupId, appId = appId, buildId = delBuildId)
            assertThatTableHasNot("metrics.build_methods", groupId, appId = appId, buildId = delBuildId)
            assertThatTableHasNot("metrics.test_session_builds", groupId, appId = appId, buildId = delBuildId)
            assertThatTableHasNot("metrics.build_method_coverage", groupId, appId = appId, buildId = delBuildId)
            assertThatTableHasNot(
                "metrics.method_daily_coverage",
                groupId,
                appId = appId,
                methodId = method1.methodId
            )
            assertThatTableHasNot(
                "metrics.test_to_code_mapping",
                groupId,
                appId = appId,
                signature = method1.signature
            )

            assertThatTableHas("metrics.builds", groupId, appId = appId, buildId = keepBuildId)
            assertThatTableHas("metrics.build_methods", groupId, appId = appId, buildId = keepBuildId)
            assertThatTableHas("metrics.test_session_builds", groupId, appId = appId, buildId = keepBuildId)
            assertThatTableHas("metrics.build_method_coverage", groupId, appId = appId, buildId = keepBuildId)
            assertThatTableHas("metrics.method_daily_coverage", groupId, appId = appId, methodId = method2.methodId)
            assertThatTableHas(
                "metrics.test_to_code_mapping",
                groupId,
                appId = appId,
                signature = method2.signature
            )
        }
    }

    @Test
    fun `delete test session should remove all raw data and metrics for the session`() {
        val groupId = testGroup
        val delSession = SessionPayload(
            groupId = groupId,
            id = "sess-del",
            testTaskId = "task",
            startedAt = Clock.System.now()
        )
        val keepSession = SessionPayload(
            groupId = groupId,
            id = "sess-keep",
            testTaskId = "task",
            startedAt = Clock.System.now()
        )

        havingData {
            build1 has listOf(method1, method2)
            (test1 of delSession) covers method1 on build1
            (test2 of keepSession) covers method2 on build1
        }.afterCalling {
            delete("/data-management/groups/$groupId/tests/sessions/${delSession.id}").assertSuccessStatus()
        }.expectThat {
            assertThatTableHasNot("metrics.test_sessions", groupId, testSessionId = delSession.id)
            assertThatTableHasNot("metrics.test_launches", groupId, testSessionId = delSession.id)
            assertThatTableHasNot("metrics.build_method_test_session_coverage", groupId, testSessionId = delSession.id)
//            assertThatTableHasNot("metrics.method_daily_coverage", groupId, methodId = method1.methodId)
//            assertThatTableHasNot("metrics.test_to_code_mapping", groupId, signature = method1.signature)

            assertThatTableHas("metrics.test_sessions", groupId, testSessionId = keepSession.id)
            assertThatTableHas("metrics.test_launches", groupId, testSessionId = keepSession.id)
            assertThatTableHas("metrics.build_method_test_session_coverage", groupId, testSessionId = keepSession.id)
//            assertThatTableHas("metrics.method_daily_coverage", groupId, methodId = method2.methodId)
//            assertThatTableHas("metrics.test_to_code_mapping", groupId, signature = method2.signature)
        }
    }

    private suspend fun tableHas(
        table: String, groupId: String,
        appId: String? = null,
        testProjectId: String? = null,
        buildId: String? = null,
        testSessionId: String? = null,
        methodId: String? = null,
        signature: String? = null,
    ): Boolean = MetricsDatabaseConfig.transaction {
        executeQueryReturnMap {
            append("SELECT 1 FROM $table")
            append(" WHERE group_id = ?", groupId)
            appendOptional(" AND app_id = ?", appId)
            appendOptional(" AND test_project_id = ?", testProjectId)
            appendOptional(" AND build_id = ?", buildId)
            appendOptional(" AND test_session_id = ?", testSessionId)
            appendOptional(" AND method_id = ?", methodId)
            appendOptional(" AND signature = ?", signature)
            append(" LIMIT 1")
        }.isNotEmpty()
    }


    private suspend fun assertThatTableHasNot(
        table: String, groupId: String,
        appId: String? = null,
        testProjectId: String? = null,
        buildId: String? = null,
        testSessionId: String? = null,
        methodId: String? = null,
        signature: String? = null,
    ) {
        val params = listOfNotNull(
            "groupId=$groupId",
            appId?.let { "appId=$it" },
            testProjectId?.let { "testProjectId=$it" },
            buildId?.let { "buildId=$it" },
            testSessionId?.let { "testSessionId=$it" },
            methodId?.let { "methodId=$it" },
            signature?.let { "signature=$it" }
        ).joinToString(", ")
        assertFalse(
            tableHas(table, groupId, appId, testProjectId, buildId, testSessionId, methodId, signature),
            "Expected table $table to have no data for $params"
        )
    }

    private suspend fun assertThatTableHas(
        table: String, groupId: String,
        appId: String? = null,
        testProjectId: String? = null,
        buildId: String? = null,
        testSessionId: String? = null,
        methodId: String? = null,
        signature: String? = null,
    ) {
        val params = listOfNotNull(
            "groupId=$groupId",
            appId?.let { "appId=$it" },
            testProjectId?.let { "testProjectId=$it" },
            buildId?.let { "buildId=$it" },
            testSessionId?.let { "testSessionId=$it" },
            methodId?.let { "methodId=$it" },
            signature?.let { "signature=$it" }
        ).joinToString(", ")
        assertTrue(
            tableHas(table, groupId, appId, testProjectId, buildId, testSessionId, methodId, signature),
            "Expected table $table to have data for $params"
        )
    }
}
