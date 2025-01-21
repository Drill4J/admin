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

import com.epam.drill.admin.metrics.job.RefreshMaterializedViewJob
import com.epam.drill.admin.metrics.job.VIEW_NAME
import org.quartz.JobBuilder

val refreshMethodsCoverageViewJob = JobBuilder.newJob(RefreshMaterializedViewJob::class.java)
    .storeDurably()
    .withDescription("Job for updating the materialized view 'matview_methods_coverage'.")
    .withIdentity("refreshMethodsCoverageViewJob", "refreshMaterializedViews")
    .usingJobData(VIEW_NAME, "matview_methods_coverage")
    .build()
