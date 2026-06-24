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

import com.epam.drill.admin.common.service.generateBuildId
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.test.MetricsDatabaseTests
import com.epam.drill.admin.test.withTransaction
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.route.payload.SingleMethodPayload
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.epam.drill.admin.writer.rawdata.table.InstanceTable
import com.epam.drill.admin.writer.rawdata.table.MethodCoverageTable
import com.epam.drill.admin.writer.rawdata.table.MethodTable
import com.epam.drill.admin.writer.rawdata.table.TestDefinitionTable
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import com.jayway.jsonpath.JsonPath
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverageAggregationApiTest : MetricsDatabaseTests({ default, metrics ->
    RawDataWriterDatabaseConfig.init(default)
    MetricsDatabaseConfig.init(metrics)
}) {
    private val build1Id = generateBuildId(
        testGroup, testApp, build1.instanceId, null, build1.buildVersion
    )

    @Test
    fun `coverage by package should aggregate methods and probes`() = havingData {
        val classAMethod = SingleMethodPayload(
            classname = "com/example/foo/ClassA",
            name = "methodA", params = "()", returnType = "void",
            probesCount = 2, probesStartPos = 0, bodyChecksum = "A00",
        )
        val classBMethod = SingleMethodPayload(
            classname = "com/example/foo/ClassB",
            name = "methodB", params = "()", returnType = "void",
            probesCount = 3, probesStartPos = 2, bodyChecksum = "B00",
        )
        build1 has listOf(classAMethod, classBMethod)
        test1 covers classAMethod with probesOf(1, 1) on build1
        test1 covers classBMethod with probesOf(0, 0, 1) on build1
    }.expectThat {
        client.get("/metrics/coverage/by-package") {
            parameter("buildId", build1Id)
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val data = JsonPath.parse(bodyAsText()).read<List<Map<String, Any>>>("$.data")
            assertEquals(1, data.size)
            assertEquals("com/example/foo", data.first()["packageName"])
            assertTrue((data.first()["methodsCount"] as Number).toInt() >= 2)
            assertTrue((data.first()["coveredProbes"] as Number).toInt() > 0)
        }
    }

    @Test
    fun `coverage by package should split slash-separated class names`() = havingData {
        val classAMethod = SingleMethodPayload(
            classname = "com/example/foo/MyClass",
            name = "methodA", params = "()", returnType = "void",
            probesCount = 2, probesStartPos = 0, bodyChecksum = "A01",
        )
        val classBMethod = SingleMethodPayload(
            classname = "com/other/bar/OtherClass",
            name = "methodB", params = "()", returnType = "void",
            probesCount = 3, probesStartPos = 2, bodyChecksum = "B01",
        )
        build1 has listOf(classAMethod, classBMethod)
    }.expectThat {
        client.get("/metrics/coverage/by-package") {
            parameter("buildId", build1Id)
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val data = JsonPath.parse(bodyAsText()).read<List<Map<String, Any>>>("$.data")
            assertTrue(data.size >= 2)
            assertTrue(data.any { it["packageName"] == "com/example/foo" })
            assertTrue(data.any { it["packageName"] == "com/other/bar" })
        }
    }

    @Test
    fun `coverage by class should filter by package name`() = havingData {
        val classAMethod = SingleMethodPayload(
            classname = "com/example/foo/ClassA",
            name = "methodA", params = "()", returnType = "void",
            probesCount = 2, probesStartPos = 0, bodyChecksum = "A02",
        )
        val classBMethod = SingleMethodPayload(
            classname = "com/other/bar/ClassB",
            name = "methodB", params = "()", returnType = "void",
            probesCount = 3, probesStartPos = 2, bodyChecksum = "B02",
        )
        build1 has listOf(classAMethod, classBMethod)
        test1 covers classAMethod with probesOf(1, 1) on build1
        test1 covers classBMethod with probesOf(0, 0, 1) on build1
    }.expectThat {
        val packageName = "com/example/foo"
        client.get("/metrics/coverage/by-class") {
            parameter("buildId", build1Id)
            parameter("packageName", packageName)
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val data = JsonPath.parse(bodyAsText()).read<List<Map<String, Any>>>("$.data")
            assertEquals(1, data.size)
            assertEquals("ClassA", data.first()["className"])
        }
    }

    @Test
    fun `coverage by buildId should return paginated methods`() = havingData {
        build1 has listOf(method1, method2)
    }.expectThat {
        client.get("/metrics/coverage") {
            parameter("buildId", build1Id)
            parameter("page", 1)
            parameter("pageSize", 10)
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = JsonPath.parse(bodyAsText())
            val data = json.read<List<Map<String, Any>>>("$.data")
            val total = json.read<Int>("$.paging.total")
            assertEquals(2, data.size)
            assertEquals(2, total)
        }
    }

    @AfterEach
    fun clearAll() = withTransaction(RawDataWriterDatabaseConfig.database) {
        MethodCoverageTable.deleteAll()
        InstanceTable.deleteAll()
        MethodTable.deleteAll()
        BuildTable.deleteAll()
        TestLaunchTable.deleteAll()
        TestSessionTable.deleteAll()
        TestDefinitionTable.deleteAll()
    }
}
