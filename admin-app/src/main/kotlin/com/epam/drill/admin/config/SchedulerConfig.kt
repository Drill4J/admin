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

class SchedulerConfig(private val config: ApplicationConfig) {
    val refreshViewsIntervalInMinutes: Int = config.propertyOrNull("refreshViewsIntervalInMinutes")?.getString()?.toInt() ?: 30
    val threadPools: Int = config.propertyOrNull("threadPools")?.getString()?.toInt() ?: 4
}

val schedulerDIModule = DI.Module("scheduler") {
    bind<SchedulerConfig>() with singleton {
        SchedulerConfig(instance<Application>().environment.config.config("drill.scheduler"))
    }
    bind<DrillScheduler>() with singleton {
        DrillScheduler(instance())
    }
}