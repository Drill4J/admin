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

import com.epam.drill.admin.metrics.job.RefreshMaterializedViewJob
import com.epam.drill.admin.metrics.repository.MetricsRepository
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
    bind<MetricsRepository>() with singleton {
        MetricsRepositoryImpl()
    }
    bind<MetricsService>() with singleton {
        val drillConfig: ApplicationConfig = instance<Application>().environment.config.config("drill")
        MetricsServiceImpl(
            metricsRepository = instance(),
            metricsServiceUiLinksConfig = MetricsServiceUiLinksConfig(drillConfig.config("metrics.ui")),
            testRecommendationsConfig = TestRecommendationsConfig(drillConfig.config("testRecommendations")),
            metricsConfig = MetricsConfig(drillConfig.config("metrics")),
        )
    }
    bind<RefreshMaterializedViewJob>() with singleton {
        RefreshMaterializedViewJob(metricsRepository = instance())
    }
}

