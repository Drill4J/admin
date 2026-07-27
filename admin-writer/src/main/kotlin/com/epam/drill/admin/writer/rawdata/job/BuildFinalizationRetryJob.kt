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
package com.epam.drill.admin.writer.rawdata.job

import com.epam.drill.admin.writer.rawdata.service.BuildValidationService
import kotlinx.coroutines.runBlocking
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext

/**
 * Periodically re-validates builds whose finalization is still PENDING, on a fixed schedule
 * (see [com.epam.drill.admin.writer.rawdata.config.buildFinalizationRetryTrigger]). A build keeps
 * being retried on every tick until it is validated successfully or until the maximum validation
 * window (measured from `finalized_at`) elapses, at which point
 * [com.epam.drill.admin.writer.rawdata.service.impl.BuildValidationServiceImpl] marks it INVALID.
 */
@DisallowConcurrentExecution
class BuildFinalizationRetryJob(
    private val buildValidationService: BuildValidationService,
) : Job {
    override fun execute(context: JobExecutionContext) {
        runBlocking {
            buildValidationService.validateAllBuilds()
        }
    }
}
