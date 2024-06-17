package com.epam.drill.admin.metrics.config

import io.ktor.server.config.*

class MetricsServiceUiLinksConfig(
    val baseUrl: String?,
    val buildTestingReportPath: String?,
    val buildComparisonReportPath: String?
) {
    constructor(config: ApplicationConfig) : this(
        baseUrl = config.propertyOrNull("baseUrl")?.getString(),
        buildTestingReportPath = config.propertyOrNull("buildTestingReportPath")?.getString(),
        buildComparisonReportPath = config.propertyOrNull("buildComparisonReportPath")?.getString()
    )
}