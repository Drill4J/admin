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
package com.epam.drill.admin.config

import com.epam.drill.admin.scheduler.DrillScheduler
import io.ktor.server.application.*
import io.ktor.server.config.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import org.quartz.CronScheduleBuilder
import org.quartz.CronTrigger
import org.quartz.SimpleScheduleBuilder
import org.quartz.SimpleTrigger
import org.quartz.TriggerBuilder

class SchedulerConfig(private val config: ApplicationConfig) {

    @Deprecated("Use refreshViewsJobCron instead")
    private val refreshViewsIntervalInMinutes: Int =
        config.propertyOrNull("refreshViewsIntervalInMinutes")?.getString()?.toInt() ?: 30
    private val refreshViewsJobCron: String =
        config.propertyOrNull("refreshViewsJobCron")?.getString()
            ?: intervalToCron(refreshViewsIntervalInMinutes)
    private val dataRetentionJobCron: String = config.propertyOrNull("dataRetentionJobCron")?.getString() ?: "0 0 1 * * ?"
    val threadPools: Int = config.propertyOrNull("threadPools")?.getString()?.toInt() ?: 2

    val refreshViewsTrigger: CronTrigger
        get() = TriggerBuilder.newTrigger()
            .withIdentity("refreshViewsTrigger", "refreshViews")
            .startNow()
            .withSchedule(
                CronScheduleBuilder.cronSchedule(refreshViewsJobCron)
            )
            .build()

    val retentionPoliciesTrigger: CronTrigger
        get() = TriggerBuilder.newTrigger()
            .withIdentity("retentionPolicyTrigger", "retentionPolicies")
            .startNow()
            .withSchedule(
                CronScheduleBuilder.cronSchedule(dataRetentionJobCron)
            )
            .build()
}

val schedulerDIModule = DI.Module("scheduler") {
    bind<SchedulerConfig>() with singleton {
        SchedulerConfig(instance<Application>().environment.config.config("drill.scheduler"))
    }
    bind<DrillScheduler>() with singleton {
        DrillScheduler(instance())
    }
}

private fun intervalToCron(interval: Int): String {
    return if (interval < 60) {
        "0 0/${interval} * * * ?"
    } else {
        val hours = interval / 60
        val minutes = interval % 60
        if (minutes == 0) {
            "0 0 0/${hours} * * ?"
        } else {
            "0 ${minutes} 0/${hours} * * ?"
        }
    }
}