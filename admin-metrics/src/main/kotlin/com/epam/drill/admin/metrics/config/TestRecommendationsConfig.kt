package com.epam.drill.admin.metrics.config

import io.ktor.server.config.*

/**
 * Configuration for test recommendations.
 */
class TestRecommendationsConfig(private val config: ApplicationConfig) {

    /**
     * Period of days from now by default to get the coverage data.
     */
    val coveragePeriodDays: Int
        get() = config.propertyOrNull("coveragePeriodDays")?.getString()?.toIntOrNull() ?: 30
}