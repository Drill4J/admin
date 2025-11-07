package com.epam.drill.admin.metrics.models

open class CoverageCriteria(
    val builds: List<Build> = emptyList(),
    val branches: List<String> = emptyList(),
    val appEnvIds: List<String> = emptyList(),
    val periodDays: Int? = null,
) {
    object NONE: CoverageCriteria()
}

