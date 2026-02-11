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

import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig.transaction
import com.epam.drill.admin.metrics.repository.MetricsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import java.time.Instant

@DisallowConcurrentExecution
class MetricsDataRetentionPolicyJob(
    private val metricsRepository: MetricsRepository,
) : Job {
    private val logger = KotlinLogging.logger {}

    override fun execute(context: JobExecutionContext) {
        val groupId: String? = context.mergedJobDataMap.getString("groupId")
        runBlocking {
            transaction {
                metricsRepository.getMetricsPeriodDays().let {
                    if (groupId != null) mapOf(groupId to (it[groupId] ?: Instant.EPOCH)) else it
                }.map { (groupId, initTimestamp) ->
                    async {
                        logger.info { "Deleting all metrics data for groupId [$groupId] older than $initTimestamp..." }
                        metricsRepository.deleteAllBuildDataCreatedBefore(groupId, initTimestamp)
                        metricsRepository.deleteAllTestDataCreatedBefore(groupId, initTimestamp)
                        metricsRepository.deleteAllDailyDataCreatedBefore(groupId, initTimestamp)
                        logger.info { "Metrics data for groupId $groupId older than $initTimestamp deleted successfully." }
                    }
                }.awaitAll()
            }
            transaction {
                logger.info { "Deleting orphan references..." }
                metricsRepository.deleteAllOrphanReferences()
                logger.info { "Orphan references deleted successfully." }
            }
        }
    }

}