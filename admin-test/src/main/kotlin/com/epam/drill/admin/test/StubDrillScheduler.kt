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
package com.epam.drill.admin.test

import com.epam.drill.admin.common.scheduler.DrillScheduler
import org.quartz.Job
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.JobKey
import org.quartz.Trigger
import org.quartz.core.jmx.JobDetailSupport
import org.quartz.impl.JobDetailImpl
import org.quartz.impl.JobExecutionContextImpl
import org.quartz.impl.triggers.SimpleTriggerImpl
import org.quartz.spi.JobFactory
import org.quartz.spi.TriggerFiredBundle
import javax.sql.DataSource

class StubDrillScheduler(
    val job: Job
) : DrillScheduler {

    override fun init(jobFactory: JobFactory, dataSource: DataSource) {
        // no-op
    }

    override fun start() {
        // no-op
    }

    override fun shutdown() {
        // no-op
    }

    override fun scheduleJob(jobDetail: JobDetail, trigger: Trigger) {
        // no-op
    }

    override fun triggerJob(
        jobKey: JobKey,
        data: JobDataMap?,
        onCompletion: ((Any?, Exception?) -> Unit)?
    ) {
        try {
            val context = executeJob(jobDetail = JobDetailImpl().apply {
                key = jobKey
                jobClass = job::class.java
                jobDataMap = data
            })
            onCompletion?.invoke(context.result, null)
        } catch (e: Exception) {
            onCompletion?.invoke(null, e)
            throw e
        }
    }

    override fun addJob(jobDetail: JobDetail) {
        // no-op
    }

    private fun executeJob(jobDetail: JobDetail): JobExecutionContext {
        val context = JobExecutionContextImpl(
            null, TriggerFiredBundle(
                jobDetail,
                SimpleTriggerImpl(),
                null,
                false, null, null, null, null
            ), job)
        job.execute(context)
        return context
    }
}