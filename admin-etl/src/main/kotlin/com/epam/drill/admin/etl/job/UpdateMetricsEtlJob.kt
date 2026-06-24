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
import com.epam.drill.admin.etl.impl.toEtlContext
import com.epam.drill.admin.etl.impl.toMap
import com.epam.drill.admin.etl.service.EtlService
import kotlinx.coroutines.runBlocking
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.JobKey
import java.time.Instant

const val DEFAULT_ETL = "metrics"

@DisallowConcurrentExecution
class UpdateMetricsEtlJob(
    private val etlService: EtlService,
) : Job {

    override fun execute(context: JobExecutionContext) {
        val etlName = context.mergedJobDataMap.getString("etl") ?: DEFAULT_ETL
        val hasGroupId: Boolean = context.mergedJobDataMap.getString("groupId") != null
        val reset: Boolean = context.mergedJobDataMap.getBooleanValue("reset")
        val initTimestamp: Instant? = context.mergedJobDataMap.getInstantValue("initTimestamp")
        val finalTimestamp: Instant? = context.mergedJobDataMap.getInstantValue("finalTimestamp")
        val etlContext = takeIf { hasGroupId }?.let {
            context.mergedJobDataMap.toEtlContext()
        }
        context.result = runBlocking {
            etlService.refresh(etlContext, etlName, reset, initTimestamp, finalTimestamp)
        }
    }

    private fun JobDataMap.getInstantValue(key: String): Instant? = get(key)?.let {
        when (it) {
            is Instant -> it
            is String -> Instant.parse(it)
            is Long -> Instant.ofEpochMilli(it)
            else -> throw IllegalArgumentException("Unsupported initTimestamp type: ${it::class}")
        }
    }
}

val updateMetricsEtlJobKey: JobKey
    get() = JobKey.jobKey("metricsEtl", "drill")

fun getUpdateMetricsEtlDataMap(groupId: String?, reset: Boolean) = JobDataMap().apply {
    groupId?.let { put("groupId", it) }
    put("reset", reset)
}

fun getJobDataMap(
    context: EtlContext? = null,
    etl: String? = null,
    reset: Boolean = false,
    initTimestamp: Instant? = null,
    finalTimestamp: Instant? = null,
): JobDataMap {
    val jobData = JobDataMap()
    context?.toMap()?.filterValues { it != null }?.forEach { (key, value) -> jobData.put(key, value) }
    etl?.let { jobData.put("etl", it) }
    jobData.put("reset", reset)
    initTimestamp?.let { jobData.put("initTimestamp", it) }
    finalTimestamp?.let { jobData.put("finalTimestamp", it) }
    return jobData
}