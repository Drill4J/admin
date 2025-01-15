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
