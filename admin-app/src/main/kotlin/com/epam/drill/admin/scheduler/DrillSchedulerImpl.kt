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
package com.epam.drill.admin.scheduler

import com.epam.drill.admin.common.scheduler.DrillScheduler
import com.epam.drill.admin.config.SchedulerConfig
import mu.KotlinLogging
import org.quartz.*
import org.quartz.DateBuilder.MILLISECONDS_IN_MINUTE
import org.quartz.impl.StdSchedulerFactory
import org.quartz.impl.jdbcjobstore.JobStoreTX
import org.quartz.impl.jdbcjobstore.PostgreSQLDelegate
import org.quartz.impl.matchers.GroupMatcher
import org.quartz.impl.matchers.KeyMatcher
import org.quartz.simpl.SimpleThreadPool
import org.quartz.spi.JobFactory
import org.quartz.utils.ConnectionProvider
import org.quartz.utils.DBConnectionManager
import java.util.*
import javax.sql.DataSource

class DrillSchedulerImpl(
    private val config: SchedulerConfig
) : DrillScheduler {
    private val logger = KotlinLogging.logger {}
    private lateinit var scheduler: Scheduler

    override fun init(jobFactory: JobFactory, dataSource: DataSource) {
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
        scheduler.setJobFactory(jobFactory)
    }

    override fun start() {
        scheduler.start()
        deleteDeprecatedJobs()
    }

    override fun scheduleJob(jobDetail: JobDetail, trigger: Trigger) {
        if (scheduler.checkExists(jobDetail.key)) {
            //Delete old triggers if they exist
            scheduler.getTriggersOfJob(jobDetail.key)
                .filter { it.key != trigger.key }
                .forEach {
                    scheduler.unscheduleJob(it.key)
                    logger.debug { "Unscheduled job '${jobDetail.key}'." }
                }
        }
        scheduler.scheduleJob(jobDetail, setOf(trigger), true)
        when (trigger) {
            is SimpleTrigger -> logger.info { "Scheduled job '${jobDetail.key}', repeat interval: ${trigger.repeatInterval.inMinutes()} minutes." }
            is CronTrigger -> logger.info { "Scheduled job '${jobDetail.key}', cron expression: ${trigger.cronExpression}." }
        }
    }

    override fun triggerJob(jobKey: JobKey, data: JobDataMap?, onCompletion: ((result: Any?, exception: Exception?) -> Unit)?) {
        try {
            val jobData = if (onCompletion != null) {
                val jobInstanceId = UUID.randomUUID().toString()
                (data ?: JobDataMap()).apply {
                    put("jobInstanceId", jobInstanceId)
                }.also {
                    addJobCompletionListener(jobKey, jobInstanceId, onCompletion)
                }
            } else data
            scheduler.triggerJob(jobKey, jobData)
            logger.info { "Triggered job '$jobKey' now." }
        } catch (e: SchedulerException) {
            logger.error(e) { "Failed to trigger job '$jobKey'." }
            throw e
        }
    }

    override fun addJob(jobDetail: JobDetail) {
        scheduler.addJob(jobDetail, true)
    }

    override fun shutdown() {
        if (!scheduler.isShutdown) {
            scheduler.shutdown(false)
        }
    }

    private fun Long.inMinutes() = this / MILLISECONDS_IN_MINUTE

    /**
     * Deletes jobs that are no longer needed.
     * This method is called during the initialization of the scheduler to clean up any old jobs.
     */
    private fun deleteDeprecatedJobs() {
        scheduler.getJobKeys(GroupMatcher.jobGroupEquals("refreshMaterializedViews"))
            .forEach { existingJobKey ->
                scheduler.deleteJob(existingJobKey)
                logger.debug { "Deleted deprecated job: $existingJobKey" }
            }
        scheduler.getJobKeys(GroupMatcher.jobGroupEquals("metricsJobs"))
            .filter { it.name == "refreshMaterializedViews" }
            .forEach { existingJobKey ->
                scheduler.deleteJob(existingJobKey)
                logger.debug { "Deleted deprecated job: $existingJobKey" }
            }
    }

    private fun addJobCompletionListener(jobKey: JobKey, jobInstanceId: String, onCompletion: (result: Any?, exception: Exception?) -> Unit) {
        val listenerName = "CompletionListener-${jobKey.name}-$jobInstanceId"
        val listener = object : JobListener {
            override fun getName(): String {
                return listenerName
            }

            override fun jobToBeExecuted(context: JobExecutionContext?) {}

            override fun jobExecutionVetoed(context: JobExecutionContext?) {}

            override fun jobWasExecuted(context: JobExecutionContext?, jobException: JobExecutionException?) {
                if (context?.jobDetail?.key == jobKey &&
                    context.mergedJobDataMap.getString("jobInstanceId") == jobInstanceId
                ) {
                    try {
                        if (jobException != null) {
                            onCompletion(null, jobException)
                        } else {
                            onCompletion(context.result, null)
                        }
                    } finally {
                        scheduler.listenerManager.removeJobListener(name)
                    }
                }
            }
        }
        scheduler.listenerManager.addJobListener(listener, KeyMatcher.keyEquals(jobKey))
    }
}

class QuartzDataSourceConnectionProvider(private val dataSource: DataSource) : ConnectionProvider {
    override fun getConnection() = dataSource.connection
    override fun shutdown() {}
    override fun initialize() {}
}