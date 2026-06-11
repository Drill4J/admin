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

import com.epam.drill.admin.common.scheduler.DrillScheduler
import com.epam.drill.admin.etl.EtlMetadataRepository
import com.epam.drill.admin.etl.EtlOrchestrator
import com.epam.drill.admin.etl.config.EtlConfig
import com.epam.drill.admin.etl.config.EtlMeter
import com.epam.drill.admin.etl.impl.EtlMetadataRepositoryImpl
import com.epam.drill.admin.etl.impl.EtlOrchestratorImpl
import com.epam.drill.admin.etl.job.DEFAULT_ETL
import com.epam.drill.admin.etl.job.UpdateMetricsEtlJob
import com.epam.drill.admin.etl.job.getUpdateMetricsEtlDataMap
import com.epam.drill.admin.etl.job.updateMetricsEtlJobKey
import com.epam.drill.admin.metrics.etl.*
import com.epam.drill.admin.etl.service.EtlService
import com.epam.drill.admin.etl.service.impl.EtlServiceImpl
import com.epam.drill.admin.writer.rawdata.config.settingsServicesDIModule
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import org.quartz.JobBuilder
import org.quartz.JobDetail

const val TEST_DEFINITION_COVERAGE_ETL = "testDefinitionCoverage"

val etlDIModule
    get() = DI.Module("etlServices") {
        importOnce(settingsServicesDIModule)
        bind<EtlMetadataRepository>() with singleton {
            EtlMetadataRepositoryImpl(
                database = MetricsDatabaseConfig.database,
                dbSchema = MetricsDatabaseConfig.dbSchema
            )
        }
        bind<EtlOrchestrator>(tag = DEFAULT_ETL) with singleton {
            val metrics = EtlMeter(instance())
            val drillConfig: ApplicationConfig = instance<Application>().environment.config.config("drill")
            val etlConfig = EtlConfig(drillConfig.config("etl"), metrics)

            with(etlConfig) {
                EtlOrchestratorImpl(
                    name = DEFAULT_ETL,
                    pipelines = listOf(
                        // Reference data
                        buildsPipeline,
                        buildMethodsPipeline,
                        methodsPipeline,
                        testLaunchesPipeline,
                        testDefinitionsPipeline,
                        testSessionsPipeline,
                        testSessionBuildsPipeline,
                        // Coverage extractor group
                        buildMethodTestSessionCoveragePipeline,
                        buildMethodCoveragePipeline,
                        methodDailyCoveragePipeline,
                        testSessionBuildsFromCoveragePipeline,
                        // Test-launch coverage extractor group
                        buildMethodTestSessionCoverageFromTestLaunchesPipeline,
                        buildMethodCoverageFromTestLaunchesPipeline,
                        methodDailyCoverageFromTestLaunchesPipeline,
                        test2CodeMappingPipeline,
                        testSessionBuildsFromTestLaunchesPipeline,
                        // Coverage by request extractor group
//                        buildMethodTestDefinitionCoverageByRequestPipeline
                    ),
                    metadataRepository = instance(),
                    consistencyWindow = consistencyWindow,
                    processingDelay = processingDelay,
                    bufferSize = bufferSize,
                )
            }
        }
        bind<EtlOrchestrator>(tag = TEST_DEFINITION_COVERAGE_ETL) with singleton {
            val metrics = EtlMeter(instance())
            val drillConfig: ApplicationConfig = instance<Application>().environment.config.config("drill")
            val etlConfig = EtlConfig(drillConfig.config("etl"), metrics)

            with(etlConfig) {
                EtlOrchestratorImpl(
                    name = TEST_DEFINITION_COVERAGE_ETL,
                    pipelines = listOf(buildMethodTestDefinitionCoveragePipeline),
                    metadataRepository = instance(),
                )
            }
        }
        bind<EtlService>() with singleton {
            EtlServiceImpl(
                scheduler = instance<DrillScheduler>(),
                etlRepository = instance()
            )
        }
        bind<UpdateMetricsEtlJob>() with singleton {
            val etlList: List<EtlOrchestrator> = listOf(
                instance(tag = DEFAULT_ETL),
                instance(tag = TEST_DEFINITION_COVERAGE_ETL)
            )
            UpdateMetricsEtlJob(
                settingsService = instance(),
                etls = etlList.associateBy { it.name },
            )
        }
    }

val updateMetricsEtlJob: JobDetail
    get() = JobBuilder.newJob(UpdateMetricsEtlJob::class.java)
        .storeDurably()
        .withDescription("Job for updating metrics using ETL processing.")
        .withIdentity(updateMetricsEtlJobKey)
        .usingJobData(getUpdateMetricsEtlDataMap(null, false))
        .build()


