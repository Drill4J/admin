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

import com.epam.drill.admin.common.scheduler.deleteMetricsDataJobKey
import com.epam.drill.admin.metrics.job.DeleteMetricsDataJob
import com.epam.drill.admin.metrics.job.MetricsDataRetentionPolicyJob
import org.quartz.*

val metricsDataRetentionPolicyJob: JobDetail
    get() = JobBuilder.newJob(MetricsDataRetentionPolicyJob::class.java)
        .storeDurably()
        .withDescription("Job for deleting metrics data older than the retention period.")
        .withIdentity("metricsRetentionPolicyJob", "drill")
        .build()
val deleteMetricsDataJob: JobDetail
    get() = JobBuilder.newJob(DeleteMetricsDataJob::class.java)
        .storeDurably()
        .withDescription("Job for synchronous deletion data with raw data schema.")
        .withIdentity(deleteMetricsDataJobKey)
        .build()