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

import com.epam.drill.admin.etl.EtlJobsRepository
import com.epam.drill.admin.etl.EtlLauncher
import com.epam.drill.admin.etl.EtlMetadataRepository
import com.epam.drill.admin.etl.EtlOrchestrator
import com.epam.drill.admin.etl.EtlWorkerPool
import com.epam.drill.admin.etl.config.EtlConfig
import com.epam.drill.admin.etl.config.EtlMeter
import com.epam.drill.admin.etl.impl.EtlJobsRepositoryImpl
import com.epam.drill.admin.etl.impl.EtlLauncherImpl
import com.epam.drill.admin.etl.impl.EtlMetadataRepositoryImpl
import com.epam.drill.admin.etl.impl.EtlOrchestratorImpl
import com.epam.drill.admin.etl.impl.SemaphoreWorkerPool
import com.epam.drill.admin.etl.job.IncrementalRunEtlJob
import com.epam.drill.admin.etl.job.RunIdleEtlJobsJob
import com.epam.drill.admin.etl.job.incrementalRunEtlJobKey
import com.epam.drill.admin.etl.job.runIdleEtlJobsJobKey
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

const val DEFAULT_ETL = "today"
const val HISTORICAL_ETL = "historical"
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
        bind<EtlJobsRepository>() with singleton {
            EtlJobsRepositoryImpl(
                database = MetricsDatabaseConfig.database,
                dbSchema = MetricsDatabaseConfig.dbSchema
            )
        }
        bind<EtlConfig>() with singleton {
            val metrics = EtlMeter(instance())
            val drillConfig: ApplicationConfig = instance<Application>().environment.config.config("drill")
            EtlConfig(drillConfig.config("etl"), metrics)
        }
        bind<EtlOrchestrator>(tag = DEFAULT_ETL) with singleton {
            val etlConfig = instance<EtlConfig>()
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
                    ),
                    metadataRepository = instance(),
                    jobsRepository = instance(),
                    metrics = metrics,
                    consistencyWindow = consistencyWindow,
                    processingDelay = processingDelay,
                    bufferSize = bufferSize,
                    lockLeaseSeconds = lockLeaseSeconds,
                )
            }
        }
        bind<EtlOrchestrator>(tag = HISTORICAL_ETL) with singleton {
            val etlConfig = instance<EtlConfig>()
            with(etlConfig) {
                EtlOrchestratorImpl(
                    name = HISTORICAL_ETL,
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
                    ),
                    metadataRepository = instance(),
                    jobsRepository = instance(),
                    metrics = metrics,
                    consistencyWindow = consistencyWindow,
                    processingDelay = processingDelay,
                    bufferSize = bufferSize,
                    lockLeaseSeconds = lockLeaseSeconds,
                )
            }
        }
        bind<EtlOrchestrator>(tag = TEST_DEFINITION_COVERAGE_ETL) with singleton {
            val etlConfig = instance<EtlConfig>()
            with(etlConfig) {
                EtlOrchestratorImpl(
                    name = TEST_DEFINITION_COVERAGE_ETL,
                    pipelines = listOf(buildMethodTestDefinitionCoveragePipeline),
                    metadataRepository = instance(),
                    jobsRepository = instance(),
                    metrics = metrics,
                    lockLeaseSeconds = lockLeaseSeconds,
                )
            }
        }
        bind<EtlWorkerPool>() with singleton {
            val etlConfig = instance<EtlConfig>()
            SemaphoreWorkerPool(maxWorkers = etlConfig.maxWorkers)
        }
        bind<EtlLauncher>(tag = DEFAULT_ETL) with singleton {
            val etlConfig = instance<EtlConfig>()
            EtlLauncherImpl(
                orchestrator = instance(tag = DEFAULT_ETL),
                jobsRepository = instance(),
                lockLeaseSeconds = etlConfig.lockLeaseSeconds,
                lockRetryDelay = etlConfig.lockRetryDelay * 1000,
                lockAttempts = etlConfig.lockAttempts,
                workerPool = instance(),
            )
        }
        bind<EtlLauncher>(tag = HISTORICAL_ETL) with singleton {
            val etlConfig = instance<EtlConfig>()
            EtlLauncherImpl(
                orchestrator = instance(tag = HISTORICAL_ETL),
                jobsRepository = instance(),
                lockLeaseSeconds = etlConfig.lockLeaseSeconds,
                lockRetryDelay = etlConfig.lockRetryDelay * 1000,
                lockAttempts = etlConfig.lockAttempts,
                workerPool = instance(),
            )
        }
        bind<EtlLauncher>(tag = TEST_DEFINITION_COVERAGE_ETL) with singleton {
            val etlConfig = instance<EtlConfig>()
            EtlLauncherImpl(
                orchestrator = instance(tag = TEST_DEFINITION_COVERAGE_ETL),
                jobsRepository = instance(),
                lockLeaseSeconds = etlConfig.lockLeaseSeconds,
                lockRetryDelay = etlConfig.lockRetryDelay * 1000,
                lockAttempts = etlConfig.lockAttempts,
                workerPool = instance(),
            )
        }
        bind<EtlService>() with singleton {
            EtlServiceImpl(
                todayLauncher = instance(tag = DEFAULT_ETL),
                historicalLauncher = instance(tag = DEFAULT_ETL),
                testDefinitionCoverageLauncher = instance(tag = TEST_DEFINITION_COVERAGE_ETL),
                settingsService = instance(),
                maxWorkers = instance<EtlConfig>().maxWorkers,
            )
        }
        bind<IncrementalRunEtlJob>() with singleton {
            IncrementalRunEtlJob(etlService = instance())
        }
        bind<RunIdleEtlJobsJob>() with singleton {
            RunIdleEtlJobsJob(etlService = instance())
        }
    }

val incrementalRunEtlJob: JobDetail
    get() = JobBuilder.newJob(IncrementalRunEtlJob::class.java)
        .storeDurably()
        .withDescription("Daily incremental ETL run (skips if already running).")
        .withIdentity(incrementalRunEtlJobKey)
        .build()

val runIdleEtlJobsJob: JobDetail
    get() = JobBuilder.newJob(RunIdleEtlJobsJob::class.java)
        .storeDurably()
        .withDescription("Resumes idle/expired-lease ETL jobs, bounded by the worker budget.")
        .withIdentity(runIdleEtlJobsJobKey)
        .build()

