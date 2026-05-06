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
package com.epam.drill.admin.etl.job

import com.epam.drill.admin.etl.EtlOrchestrator
import com.epam.drill.admin.writer.rawdata.service.SettingsService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import java.time.Instant
import java.time.ZoneOffset.UTC

@DisallowConcurrentExecution
class UpdateMetricsEtlJob(
    private val settingsService: SettingsService,
    private val etl: EtlOrchestrator,
) : Job {

    override fun execute(context: JobExecutionContext) {
        val groupId: String? = context.mergedJobDataMap.getString("groupId")
        val reset: Boolean = context.mergedJobDataMap.getBooleanValue("reset")

        runBlocking {
            val results = settingsService.getAllGroupSettings().let {
                if (groupId != null) mapOf(groupId to (it[groupId])) else it
            }.map { (groupId, groupSettings) ->
                val initTimestamp = groupSettings?.metricsPeriodDays?.let {
                    Instant.now().atZone(UTC).toLocalDate().minusDays(it.toLong()).atStartOfDay().toInstant(UTC)
                } ?: Instant.EPOCH
                groupId to initTimestamp
            }.map { (groupId, initTimestamp) ->
                async {
                    if (reset)
                        etl.rerun(groupId, initTimestamp, withDataDeletion = true)
                    else
                        etl.run(groupId, initTimestamp)
                }
            }.awaitAll().flatten()
            context.result = results
        }
    }
}