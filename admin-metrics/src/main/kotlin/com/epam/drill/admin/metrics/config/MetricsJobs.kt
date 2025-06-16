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
import com.epam.drill.admin.metrics.service.impl.buildsComparisonView
import com.epam.drill.admin.metrics.service.impl.buildsCoverageView
import com.epam.drill.admin.metrics.service.impl.buildsView
import com.epam.drill.admin.metrics.service.impl.methodsCoverageView
import com.epam.drill.admin.metrics.service.impl.methodsView
import com.epam.drill.admin.metrics.service.impl.testSessionBuildsCoverageView
import com.epam.drill.admin.metrics.service.impl.testedBuildsComparisonView
import org.quartz.*

val refreshCoverageViewJob = createJob(
    "refreshMethodsCoverageViewJob",
    "$methodsCoverageView, $buildsCoverageView, $testSessionBuildsCoverageView"
)
val refreshCoverageViewTrigger: TriggerBuilder<Trigger>
    get() = createTrigger("refreshMethodsCoverageViewTrigger")

val refreshTestedBuildsComparisonViewJob = createJob(
    "refreshTestedBuildsComparisonViewJob",
    testedBuildsComparisonView
)
val refreshTestedBuildsComparisonViewTrigger: TriggerBuilder<Trigger>
    get() = createTrigger("refreshTestedBuildsComparisonViewTrigger")

val refreshBuildsViewJob = createJob(
    "refreshBuildsViewJob",
    "$methodsView, $buildsView,$buildsComparisonView"
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