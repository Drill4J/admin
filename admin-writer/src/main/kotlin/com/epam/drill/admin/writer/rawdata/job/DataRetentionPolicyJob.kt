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

import com.epam.drill.admin.writer.rawdata.repository.*
import mu.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import java.time.LocalDate
import java.time.ZoneId

@DisallowConcurrentExecution
class DataRetentionPolicyJob(
    private val groupSettingsRepository: GroupSettingsRepository,
    private val instanceRepository: InstanceRepository,
    private val coverageRepository: CoverageRepository,
    private val testSessionRepository: TestSessionRepository,
    private val testLaunchRepository: TestLaunchRepository
) : Job {
    private val logger = KotlinLogging.logger {}

    override fun execute(context: JobExecutionContext) = transaction {
        groupSettingsRepository.getAll().forEach {  settings ->
            val groupId = settings.groupId
            val retentionPeriodDays = settings.retentionPeriodDays ?: return@forEach
            val createdBefore: LocalDate = LocalDate.now(ZoneId.systemDefault()).minusDays(retentionPeriodDays.toLong())
            logger.debug { "Deleting all data of $groupId older than $createdBefore..." }
            coverageRepository.deleteAllCreatedBefore(groupId, createdBefore)
            instanceRepository.deleteAllCreatedBefore(groupId, createdBefore)
            testLaunchRepository.deleteAllCreatedBefore(groupId, createdBefore)
            testSessionRepository.deleteAllCreatedBefore(groupId, createdBefore)
            logger.debug { "Data of $groupId older than $createdBefore deleted successfully." }
        }
    }

}