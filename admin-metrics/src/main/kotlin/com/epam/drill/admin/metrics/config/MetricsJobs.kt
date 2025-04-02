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
import org.quartz.*

val refreshMethodsCoverageViewJob = createJob(
    "refreshMethodsCoverageViewJob",
    "matview_methods_coverage_v2", "matview_builds_coverage"
)
val refreshMethodsCoverageViewTrigger: TriggerBuilder<Trigger>
    get() = createTrigger("refreshMethodsCoverageViewTrigger")

val refreshTestedBuildsComparisonViewJob = createJob(
    "refreshTestedBuildsComparisonViewJob",
    "matview_tested_builds_comparison"
)
val refreshTestedBuildsComparisonViewTrigger: TriggerBuilder<Trigger>
    get() = createTrigger("refreshTestedBuildsComparisonViewTrigger")

val refreshMethodsWithRulesViewJob = createJob(
    "refreshMethodsWithRulesViewJob",
    "matview_methods_with_rules"
)
val refreshMethodsWithRulesViewTrigger: TriggerBuilder<Trigger>
    get() = createTrigger("refreshMethodsWithRulesViewTrigger")

val refreshBuildsViewJob = createJob(
    "refreshBuildsViewJob",
    "matview_builds"
)
val refreshBuildsViewTrigger: TriggerBuilder<Trigger>
    get() = createTrigger("refreshBuildsViewTrigger")

private fun createJob(jobName: String, vararg viewNames: String): JobDetail =
    JobBuilder.newJob(RefreshMaterializedViewJob::class.java)
        .storeDurably()
        .withDescription("Job for updating the materialized view '${viewNames.joinToString()}'.")
        .withIdentity(jobName, "refreshMaterializedViews")
        .usingJobData(VIEW_NAME, viewNames.joinToString())
        .build()

private fun createTrigger(triggerName: String): TriggerBuilder<Trigger> = TriggerBuilder.newTrigger()
    .withIdentity(triggerName, "refreshMaterializedViews")