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
package com.epam.drill.admin.writer.rawdata.route.payload

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// TODO rework alongside with Java Autotest Agent
@Serializable
class AddTestsPayload(
    val groupId: String,
    val sessionId: String,
    val tests: List<TestLaunchInfo> = emptyList(),
)

@Serializable
class TestLaunchInfo(
    val testLaunchId: String,
    val testDefinitionId: String,
    val result: TestResult,
    val duration: Int? = null,
    val details: TestDetails,
)

@Serializable
class TestDetails @JvmOverloads constructor(
    val runner: String = "",
    val path: String = "",
    val testName: String = "",
    val testParams: List<String> = emptyList(),
    val metadata: JsonElement? = null,
    val tags: List<String> = emptyList(),
)

enum class TestResult {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED,
    SMART_SKIPPED,
    UNKNOWN
}

@Serializable
class SingleSessionBuildPayload(
    val appId: String,
    val instanceId: String? = null,
    val commitSha: String? = null,
    val buildVersion: String? = null,
)

@Serializable
class SessionPayload(
    val id: String,
    val groupId: String,
    val testTaskId: String,
    val startedAt: Instant,
    val builds: List<SingleSessionBuildPayload> = emptyList(),
)