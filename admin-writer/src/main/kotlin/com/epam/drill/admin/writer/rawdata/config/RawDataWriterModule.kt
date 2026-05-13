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
package com.epam.drill.admin.writer.rawdata.config

import com.epam.drill.admin.writer.rawdata.job.DataRetentionPolicyJob
import com.epam.drill.admin.writer.rawdata.queue.impl.ChannelDataQueue
import com.epam.drill.admin.writer.rawdata.service.QueuedRawDataWriter
import com.epam.drill.admin.writer.rawdata.queue.impl.deserializeJson
import com.epam.drill.admin.writer.rawdata.queue.impl.toPayloadType
import com.epam.drill.admin.writer.rawdata.repository.*
import com.epam.drill.admin.writer.rawdata.repository.impl.*
import com.epam.drill.admin.writer.rawdata.service.DataManagementService
import com.epam.drill.admin.writer.rawdata.service.RawDataWriter
import com.epam.drill.admin.writer.rawdata.service.SettingsService
import com.epam.drill.admin.writer.rawdata.service.impl.DataManagementServiceImpl
import com.epam.drill.admin.writer.rawdata.service.impl.RawDataServiceImpl
import com.epam.drill.admin.writer.rawdata.service.impl.SettingsServiceImpl
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import org.quartz.JobBuilder
import org.quartz.JobDetail

val rawDataDIModule
    get() = DI.Module("rawDataServices") {
        importOnce(rawDataServicesDIModule)
        importOnce(settingsServicesDIModule)
        importOnce(dataManagementServicesDIModule)

        bind<DataRetentionPolicyJob>() with singleton {
            DataRetentionPolicyJob(
                groupSettingsRepository = instance(),
                instanceRepository = instance(),
                coverageRepository = instance(),
                testSessionRepository = instance(),
                testLaunchRepository = instance(),
                methodRepository = instance(),
                buildRepository = instance(),
            )
        }
    }

val rawDataServicesDIModule
    get() = DI.Module("rawDataWriterServices") {
        bind<RawDataQueueConfig>() with singleton {
            val drillConfig: ApplicationConfig = instance<Application>().environment.config.config("drill")
            RawDataQueueConfig(drillConfig.config("rawData.queue"))
        }
        bind<InstanceRepository>() with singleton { InstanceRepositoryImpl() }
        bind<BuildRepository>() with singleton { BuildRepositoryImpl() }
        bind<MethodRepository>() with singleton { MethodRepositoryImpl() }
        bind<CoverageRepository>() with singleton { CoverageRepositoryImpl() }
        bind<TestDefinitionRepository>() with singleton { TestDefinitionRepositoryImpl() }
        bind<TestSessionRepository>() with singleton { TestSessionRepositoryImpl() }
        bind<TestSessionBuildRepository>() with singleton { TestSessionBuildRepositoryImpl() }
        bind<TestLaunchRepository>() with singleton { TestLaunchRepositoryImpl() }

        bind<RawDataWriter>() with singleton {
            RawDataServiceImpl(
                instanceRepository = instance(),
                coverageRepository = instance(),
                testDefinitionRepository = instance(),
                testLaunchRepository = instance(),
                methodRepository = instance(),
                buildRepository = instance(),
                testSessionRepository = instance(),
                testSessionBuildRepository = instance(),
            )
        }
        bind<QueuedRawDataWriter>() with singleton {
            val config = instance<RawDataQueueConfig>()
            val writer = instance<RawDataWriter>()
            QueuedRawDataWriter(
                handler = writer,
                workers = config.workers,
                queue = ChannelDataQueue(
                    deserializer = { route, bytes -> bytes.deserializeJson(route.toPayloadType()) },
                    capacity = config.capacity
                )
            )
        }
    }

val settingsServicesDIModule
    get() = DI.Module("settingsServices") {
        bind<GroupRepository>() with singleton { GroupRepositoryImpl() }
        bind<GroupSettingsRepository>() with singleton { GroupSettingsRepositoryImpl() }
        bind<SettingsService>() with singleton {
            SettingsServiceImpl(
                groupSettingsRepository = instance(),
                groupRepository = instance()
            )
        }
    }

val dataManagementServicesDIModule
    get() = DI.Module("dataManagementServices") {
        bind<MethodIgnoreRuleRepository>() with singleton { MethodIgnoreRuleRepositoryImpl() }
        bind<DataManagementService>() with singleton {
            DataManagementServiceImpl(
                instanceRepository = instance(),
                buildRepository = instance(),
                methodRepository = instance(),
                coverageRepository = instance(),
                testSessionRepository = instance(),
                testLaunchRepository = instance(),
                testSessionBuildRepository = instance(),
                methodIgnoreRuleRepository = instance(),
                scheduler = instance(),
            )
        }
    }

val rawDataRetentionPolicyJob: JobDetail
    get() = JobBuilder.newJob(DataRetentionPolicyJob::class.java)
        .storeDurably()
        .withDescription("Job for deleting raw data older than the retention period.")
        .withIdentity("rawDataRetentionPolicyJob", "drill")
        .build()