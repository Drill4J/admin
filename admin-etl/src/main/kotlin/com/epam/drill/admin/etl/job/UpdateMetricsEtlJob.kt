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

import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.service.EtlService
import kotlinx.coroutines.runBlocking
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobKey

const val DEFAULT_ETL = "metrics"

@DisallowConcurrentExecution
class UpdateMetricsEtlJob(
    private val etlService: EtlService,
) : Job {

    override fun execute(context: JobExecutionContext) {
        val groupId = context.mergedJobDataMap.getString("groupId")
        context.result = runBlocking {
            etlService.refresh(groupId = groupId, skipIfLocked = true)
        }
    }
}

val updateMetricsEtlJobKey: JobKey
    get() = JobKey.jobKey("metricsEtl", "drill")