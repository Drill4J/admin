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
import org.kodein.di.DI
import org.quartz.*
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
    private lateinit var scheduler: Scheduler

    fun init(di: DI, dataSource: DataSource) {
        val quartzProperties = Properties().apply {
            setProperty("org.quartz.scheduler.instanceName", "MetricsScheduler")
            setProperty("org.quartz.scheduler.instanceId", "AUTO")
            setProperty("org.quartz.threadPool.class", SimpleThreadPool::class.java.name)
            setProperty("org.quartz.threadPool.threadCount", config.threadPools.toString())

            setProperty("org.quartz.jobStore.class", JobStoreTX::class.java.name)
            setProperty("org.quartz.jobStore.driverDelegateClass", PostgreSQLDelegate::class.java.name)

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
        val refreshMethodsCoverageViewJob = JobBuilder.newJob(RefreshMaterializedViewJob::class.java)
            .withIdentity("refreshMethodsCoverageViewJob", "refreshMaterializedViews")
            .usingJobData(VIEW_NAME, "matview_methods_coverage")
            .build()

        val refreshViewIntervalTrigger = TriggerBuilder.newTrigger()
            .startNow()
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInMinutes(config.refreshViewsIntervalInMinutes)
                    .repeatForever()
            )
            .build()

        scheduler.start()

        scheduleJob(refreshMethodsCoverageViewJob, refreshViewIntervalTrigger)
    }

    fun scheduleJob(jobDetail: JobDetail, trigger: Trigger) {
        if (!scheduler.checkExists(jobDetail.key)){
            scheduler.scheduleJob(jobDetail, trigger)
        }
    }

    fun shutdown() {
        if (!scheduler.isShutdown) {
            scheduler.shutdown(true)
        }
    }
}

class QuartzDataSourceConnectionProvider(private val dataSource: DataSource) : ConnectionProvider {
    override fun getConnection() = dataSource.connection
    override fun shutdown() {}
    override fun initialize() {}
}