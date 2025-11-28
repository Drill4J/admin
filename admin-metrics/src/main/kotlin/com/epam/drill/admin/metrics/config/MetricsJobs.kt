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

import com.epam.drill.admin.metrics.job.MetricsDataRetentionPolicyJob
import com.epam.drill.admin.metrics.job.UpdateMetricsEtlJob
import org.quartz.*

fun getUpdateMetricsEtlDataMap(reset: Boolean) = JobDataMap().apply {
    put("reset", reset)
}
val updateMetricsEtlJobKey: JobKey
    get() = JobKey.jobKey("metricsEtl", "metricsJobs")
val updateMetricsEtlJob: JobDetail
    get() = JobBuilder.newJob(UpdateMetricsEtlJob::class.java)
        .storeDurably()
        .withDescription("Job for updating metrics using ETL processing.")
        .withIdentity(updateMetricsEtlJobKey)
        .usingJobData(getUpdateMetricsEtlDataMap(false))
        .build()
val metricsDataRetentionPolicyJob: JobDetail
    get() = JobBuilder.newJob(MetricsDataRetentionPolicyJob::class.java)
        .storeDurably()
        .withDescription("Job for deleting metrics data older than the retention period.")
        .withIdentity("metricsRetentionPolicyJob", "metricsJobs")
        .build()