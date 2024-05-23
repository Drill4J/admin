package com.epam.drill.admin.writer.rawdata.route.payload

import kotlinx.serialization.Serializable

// TODO rework alongside with Java Autotest Agent
@Serializable
class AddTestsPayload(
    val sessionId: String,
    val tests: List<TestInfo> = emptyList(),
)

@Serializable
class TestInfo(
    val id: String,
    val result: TestResult,
    val startedAt: Long,
    val finishedAt: Long,
    val details: TestDetails,
)

@Serializable
class Label(
    val name: String,
    val value: String,
)

@Serializable
class TestDetails @JvmOverloads constructor(
    val engine: String = "",
    val path: String = "",
    val testName: String = "",
    val params: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
    val labels: Set<Label> = emptySet(),
)

enum class TestResult {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED,
    UNKNOWN
}
