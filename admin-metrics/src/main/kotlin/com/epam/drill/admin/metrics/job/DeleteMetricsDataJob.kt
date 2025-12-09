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
package com.epam.drill.admin.metrics.job

import com.epam.drill.admin.metrics.repository.MetricsRepository
import kotlinx.coroutines.runBlocking
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext

@DisallowConcurrentExecution
class DeleteMetricsDataJob(
    private val metricsRepository: MetricsRepository,
) : Job {
    override fun execute(context: JobExecutionContext) {
        val dataType = context.mergedJobDataMap.getString("dataType")
        val groupId = context.mergedJobDataMap.getString("groupId")
        runBlocking {
            when (dataType) {
                "build" -> {
                    val appId = context.mergedJobDataMap.getString("appId")
                    val buildId = context.mergedJobDataMap.getString("buildId")
                    metricsRepository.deleteAllBuildDataByBuildId(groupId, appId, buildId)
                }
                "testSession" -> {
                    val testSessionId = context.mergedJobDataMap.getString("testSessionId")
                    metricsRepository.deleteAllTestDataByTestSessionId(groupId, testSessionId)
                }
                else -> throw IllegalArgumentException("Unknown dataType: $dataType")
            }
        }
    }
}