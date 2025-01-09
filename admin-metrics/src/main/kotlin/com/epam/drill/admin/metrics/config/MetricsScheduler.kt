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
import mu.KotlinLogging
import org.kodein.di.DI
import org.quartz.*
import org.quartz.DateBuilder.MILLISECONDS_IN_MINUTE
import org.quartz.impl.StdSchedulerFactory
import org.quartz.impl.jdbcjobstore.JobStoreTX
import org.quartz.impl.jdbcjobstore.PostgreSQLDelegate
import org.quartz.simpl.SimpleThreadPool
import org.quartz.utils.ConnectionProvider
import org.quartz.utils.DBConnectionManager
import java.util.*
import javax.sql.DataSource

class MetricsScheduler(
    private val config: SchedulerConfig
) {
    private val logger = KotlinLogging.logger {}
    private lateinit var scheduler: Scheduler

    private val refreshMethodsCoverageViewJob = JobBuilder.newJob(RefreshMaterializedViewJob::class.java)
        .storeDurably()
        .withDescription("Job for updating the materialized view 'matview_methods_coverage'.")
        .withIdentity("refreshMethodsCoverageViewJob", "refreshMaterializedViews")
        .usingJobData(VIEW_NAME, "matview_methods_coverage")
        .build()

    private val refreshViewIntervalTrigger = TriggerBuilder.newTrigger()
        .withIdentity("refreshMethodsCoverageViewTrigger", "refreshMaterializedViews")
        .startNow()
        .withSchedule(
            SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMinutes(config.refreshViewsIntervalInMinutes)
                .repeatForever()
                .withMisfireHandlingInstructionNextWithExistingCount()
        )
        .build()

    fun init(di: DI, dataSource: DataSource) {
        val quartzProperties = Properties().apply {
            setProperty("org.quartz.scheduler.instanceName", "MetricsScheduler")
            setProperty("org.quartz.scheduler.instanceId", "AUTO")
            setProperty("org.quartz.threadPool.class", SimpleThreadPool::class.java.name)
            setProperty("org.quartz.threadPool.threadCount", config.threadPools.toString())
            setProperty("org.quartz.jobStore.class", JobStoreTX::class.java.name)
            setProperty("org.quartz.jobStore.driverDelegateClass", PostgreSQLDelegate::class.java.name)
            setProperty("org.quartz.jobStore.isClustered", "true")

            setProperty("org.quartz.jobStore.tablePrefix", "quartz.")
            setProperty("org.quartz.jobStore.dataSource", "schedulerDS")
        }

        val schedulerFactory = StdSchedulerFactory(quartzProperties)
        val provider = QuartzDataSourceConnectionProvider(dataSource)
        DBConnectionManager.getInstance().addConnectionProvider("schedulerDS", provider)

        scheduler = schedulerFactory.scheduler
        scheduler.setJobFactory(KodeinJobFactory(di))
    }

    fun start() {
        scheduleJob(refreshMethodsCoverageViewJob, refreshViewIntervalTrigger)
        scheduler.start()
    }

    fun scheduleJob(jobDetail: JobDetail, trigger: SimpleTrigger) {
        if (!scheduler.checkExists(jobDetail.key)) {
            scheduler.scheduleJob(jobDetail, trigger)
            logger.info { "Scheduled job '${jobDetail.key}', repeat interval: ${trigger.repeatInterval.inMinutes()} minutes." }
        } else {
            scheduler.addJob(jobDetail, true)
            val previousRepeatInterval = scheduler.getTrigger(trigger.key)
                .takeIf { it is SimpleTrigger }
                ?.let { it as SimpleTrigger }
                ?.repeatInterval ?: -1L
            if (trigger.repeatInterval != previousRepeatInterval) {
                scheduler.rescheduleJob(trigger.key, trigger)
                logger.info { "Rescheduled job '${jobDetail.key}', repeat interval was changed from ${previousRepeatInterval.inMinutes()} minutes to ${trigger.repeatInterval.inMinutes()} minutes." }
            }
        }
    }

    fun shutdown() {
        if (!scheduler.isShutdown) {
            scheduler.shutdown(false)
        }
    }

    private fun Long.inMinutes() = this / MILLISECONDS_IN_MINUTE
}

class QuartzDataSourceConnectionProvider(private val dataSource: DataSource) : ConnectionProvider {
    override fun getConnection() = dataSource.connection
    override fun shutdown() {}
    override fun initialize() {}
}