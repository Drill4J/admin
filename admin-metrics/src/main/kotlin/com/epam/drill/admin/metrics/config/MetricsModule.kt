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
package com.epam.drill.admin.metrics.config

import com.epam.drill.admin.metrics.repository.impl.MetricsRepositoryImpl
import com.epam.drill.admin.metrics.service.MetricsService
import com.epam.drill.admin.metrics.service.impl.MetricsServiceImpl
import io.ktor.server.application.*
import io.ktor.server.config.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val metricsDIModule = DI.Module("metricsServices") {
    bind<MetricsService>() with singleton {
        val metricsConfig: ApplicationConfig = instance<Application>().environment.config.config("drill.metrics")
        val metricsUiConfig = metricsConfig.config("ui")

        val baseUrl = metricsUiConfig.propertyOrNull("baseUrl")
            ?.getString()
            ?.takeIf { it.isNotBlank() }

        val buildComparisonReportPath = metricsUiConfig.propertyOrNull("buildComparisonReportPath")
            ?.getString()
            ?.takeIf { it.isNotBlank() }
            ?: "/dashboard/3" // TODO should probably throw

        val buildReportPath = metricsUiConfig.propertyOrNull("buildComparisonReportPath")
            ?.getString()
            ?.takeIf { it.isNotBlank() }
            ?: "/dashboard/2" // TODO should probably throw
        // TODO I don't like the fact we have paths "knowledge" here, but the params definition elsewhere
        //          - implement mapping fns?

        // TODO pass config class instead of individual variables
        MetricsServiceImpl(
            MetricsRepositoryImpl(),
            baseUrl,
            buildComparisonReportPath,
            buildReportPath
        )
    }
}