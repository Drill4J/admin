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

import com.epam.drill.admin.writer.rawdata.route.payload.SingleMethodPayload
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverageApiTest : MetricsApiTests() {
    @Test
    fun `given build with no coverage, coverage service should return methods list with zero coverage`() {
        havingData {
            build1 has listOf(method1, method2)
        }.expectThat {
            client.get("/metrics/coverage") {
                parameter("groupId", build1.groupId)
                parameter("appId", build1.appId)
                parameter("buildVersion", build1.buildVersion)
            }.returns { data ->
                assertEquals(2, data.size)
                assertTrue(data.all { it["coveredProbes"] == 0 })
            }
        }
    }
    @Test
    fun `given build with coverage, coverage service should return methods list with coverage data`() {
        havingData {
            build1 has listOf(method1, method2)
            test1 covers method1 with probesOf(1, 1) on build1
            test1 covers method2 with probesOf(0, 0, 1) on build1
        }.expectThat {
            client.get("/metrics/coverage") {
                parameter("groupId", build1.groupId)
                parameter("appId", build1.appId)
                parameter("buildVersion", build1.buildVersion)
            }.returns { data ->
                assertEquals(2, data.size)
                assertTrue(data.any { it["name"] == method1.name && it["coveredProbes"] == 2 })
                assertTrue(data.any { it["name"] == method2.name && it["coveredProbes"] == 1 })
            }
        }
    }
    @Test
    fun `given testProjectIds filter, coverage should return only matching test project coverage`() {
        havingData {
            build1 has listOf(method1, method2)
            test1 of session1.testProjectId("project-a") covers method1 with probesOf(1, 1) on build1
            test2 of session2.testProjectId("project-b") covers method2 with probesOf(1, 1, 1) on build1
        }.expectThat {
            client.get("/metrics/coverage") {
                parameter("groupId", build1.groupId)
                parameter("appId", build1.appId)
                parameter("buildVersion", build1.buildVersion)
                parameter("testProjectIds", "project-a")
            }.returns { data ->
                assertTrue(data.any { it["name"] == method1.name && it["coveredProbes"] == 2 })
                assertTrue(data.any { it["name"] == method2.name && it["coveredProbes"] == 0 })
            }
        }
    }
    @Test
    fun `given page and size, coverage service should return methods only for specified page and size`() {
        havingData {
            val methods = (1..15).map { idx ->
                SingleMethodPayload(
                    classname = testClass,
                    name = "method$idx",
                    params = "()",
                    returnType = "void",
                    probesCount = 3,
                    probesStartPos = 3 * (idx - 1),
                    bodyChecksum = "100$idx",
                )
            }
            build1 has methods.toList()
        }.expectThat {
            client.get("/metrics/coverage") {
                parameter("groupId", build1.groupId)
                parameter("appId", build1.appId)
                parameter("buildVersion", build1.buildVersion)
                parameter("page", 1)
                parameter("pageSize", 10)
            }.returns { data ->
                assertEquals(10, data.size)
            }

            client.get("/metrics/coverage") {
                parameter("groupId", build1.groupId)
                parameter("appId", build1.appId)
                parameter("buildVersion", build1.buildVersion)
                parameter("page", 2)
                parameter("pageSize", 10)
            }.returns { data ->
                assertEquals(5, data.size)
            }
        }
    }
}