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
package com.epam.drill.admin.etl.config

import com.epam.drill.admin.common.scheduler.DrillScheduler
import com.epam.drill.admin.etl.EtlMetadataRepository
import com.epam.drill.admin.etl.EtlOrchestrator
import com.epam.drill.admin.etl.impl.EtlMetadataRepositoryImpl
import com.epam.drill.admin.etl.impl.EtlOrchestratorImpl
import com.epam.drill.admin.etl.job.UpdateMetricsEtlJob
import com.epam.drill.admin.etl.metrics.buildsPipeline
import com.epam.drill.admin.etl.metrics.coveragePipeline
import com.epam.drill.admin.etl.metrics.methodsPipeline
import com.epam.drill.admin.etl.metrics.testDefinitionsPipeline
import com.epam.drill.admin.etl.metrics.testLaunchCoveragePipeline
import com.epam.drill.admin.etl.metrics.testLaunchesPipeline
import com.epam.drill.admin.etl.metrics.testSessionBuildsPipeline
import com.epam.drill.admin.etl.metrics.testSessionsPipeline
import com.epam.drill.admin.etl.service.EtlService
import com.epam.drill.admin.etl.service.impl.EtlServiceImpl
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.writer.rawdata.config.settingsServicesDIModule
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobKey

val etlDIModule
    get() = DI.Module("etlServices") {
        importOnce(settingsServicesDIModule)
        bind<EtlMetadataRepository>() with singleton {
            EtlMetadataRepositoryImpl(
                database = MetricsDatabaseConfig.database,
                dbSchema = MetricsDatabaseConfig.dbSchema
            )
        }
        bind<EtlOrchestrator>() with singleton {
            val drillConfig: ApplicationConfig = instance<Application>().environment.config.config("drill")
            val etlConfig = EtlConfig(drillConfig.config("etl"))
            with(etlConfig) {
                EtlOrchestratorImpl(
                    name = "metrics",
                    pipelines = listOf(
                        buildsPipeline, methodsPipeline,
                        testLaunchesPipeline, testDefinitionsPipeline, testSessionsPipeline,
                        coveragePipeline, testLaunchCoveragePipeline, testSessionBuildsPipeline
                    ),
                    metadataRepository = instance(),
                    consistencyWindow = etlConfig.consistencyWindow,
                    processingDelay = etlConfig.processingDelay
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
            UpdateMetricsEtlJob(
                settingsService = instance(),
                etl = instance()
            )
        }
    }

val updateMetricsEtlJobKey: JobKey
    get() = JobKey.jobKey("metricsEtl", "drill")

fun getUpdateMetricsEtlDataMap(groupId: String?, reset: Boolean) = JobDataMap().apply {
    groupId?.let { put("groupId", it) }
    put("reset", reset)
}

val updateMetricsEtlJob: JobDetail
    get() = JobBuilder.newJob(UpdateMetricsEtlJob::class.java)
        .storeDurably()
        .withDescription("Job for updating metrics using ETL processing.")
        .withIdentity(updateMetricsEtlJobKey)
        .usingJobData(getUpdateMetricsEtlDataMap(null, false))
        .build()


