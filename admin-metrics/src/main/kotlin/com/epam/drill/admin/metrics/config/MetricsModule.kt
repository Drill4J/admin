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

import com.epam.drill.admin.etl.impl.EtlOrchestratorImpl
import com.epam.drill.admin.etl.impl.EtlMetadataRepositoryImpl
import com.epam.drill.admin.etl.impl.EtlPipelineImpl
import com.epam.drill.admin.metrics.etl.EtlMetadataRepository
import com.epam.drill.admin.metrics.etl.buildMethodsPipeline
import com.epam.drill.admin.metrics.etl.buildsPipeline
import com.epam.drill.admin.metrics.etl.coverageExtractor
import com.epam.drill.admin.metrics.etl.coveragePipeline
import com.epam.drill.admin.metrics.etl.methodCoverageLoader
import com.epam.drill.admin.metrics.etl.methodsPipeline
import com.epam.drill.admin.metrics.etl.testDefinitionsPipeline
import com.epam.drill.admin.metrics.etl.testLaunchesPipeline
import com.epam.drill.admin.metrics.etl.testSessionsPipeline
import com.epam.drill.admin.metrics.job.UpdateMetricsEtlJob
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
        RefreshMaterializedViewJob(metricsService = instance())
    }
    bind<EtlMetadataRepository>() with singleton {
        EtlMetadataRepositoryImpl(
            database = MetricsDatabaseConfig.database,
            dbSchema = MetricsDatabaseConfig.dbSchema
        )
    }
    bind<UpdateMetricsEtlJob>() with singleton {
        UpdateMetricsEtlJob(
            orchestrator = EtlOrchestratorImpl(
                pipelines = listOf(
                    buildsPipeline, methodsPipeline, buildMethodsPipeline,
                    testLaunchesPipeline, testDefinitionsPipeline, testSessionsPipeline,
                    coveragePipeline
                ),
                metadataRepository = instance()
            )
        )
    }
}

