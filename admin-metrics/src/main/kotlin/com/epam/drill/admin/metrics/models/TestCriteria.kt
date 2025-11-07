package com.epam.drill.admin.metrics.models

open class TestCriteria(
    val testTags: List<String> = emptyList(),
    val testPath: String? = null,
    val testName: String? = null,
    val testTaskId: String? = null,
) {
    object NONE: TestCriteria()
}