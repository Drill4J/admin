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
package com.epam.drill.admin.writer.rawdata.service.impl

import com.epam.drill.admin.common.exception.InvalidParameters
import com.epam.drill.admin.common.principal.User
import com.epam.drill.admin.common.scheduler.DrillScheduler
import com.epam.drill.admin.common.scheduler.deleteMetricsDataJobKey
import com.epam.drill.admin.common.scheduler.getBuildDataDeletionDataMap
import com.epam.drill.admin.common.scheduler.getTestDataDeletionDataMap
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig.transaction
import com.epam.drill.admin.writer.rawdata.entity.MethodIgnoreRule
import com.epam.drill.admin.writer.rawdata.repository.BuildRepository
import com.epam.drill.admin.writer.rawdata.repository.CoverageRepository
import com.epam.drill.admin.writer.rawdata.repository.InstanceRepository
import com.epam.drill.admin.writer.rawdata.repository.MethodIgnoreRuleRepository
import com.epam.drill.admin.writer.rawdata.repository.MethodRepository
import com.epam.drill.admin.writer.rawdata.repository.TestLaunchCoverageRequestRepository
import com.epam.drill.admin.writer.rawdata.repository.TestLaunchRepository
import com.epam.drill.admin.writer.rawdata.repository.TestSessionBuildRepository
import com.epam.drill.admin.writer.rawdata.repository.TestSessionRepository
import com.epam.drill.admin.writer.rawdata.route.payload.MethodIgnoreRulePayload
import com.epam.drill.admin.writer.rawdata.service.DataManagementService
import com.epam.drill.admin.writer.rawdata.views.MethodIgnoreRuleView

class DataManagementServiceImpl(
    private val buildRepository: BuildRepository,
    private val testSessionRepository: TestSessionRepository,
    private val coverageRepository: CoverageRepository,
    private val instanceRepository: InstanceRepository,
    private val methodRepository: MethodRepository,
    private val testSessionBuildRepository: TestSessionBuildRepository,
    private val testLaunchRepository: TestLaunchRepository,
    private val methodIgnoreRuleRepository: MethodIgnoreRuleRepository,
    private val testLaunchCoverageRequestRepository: TestLaunchCoverageRequestRepository,
    private val scheduler: DrillScheduler,
) : DataManagementService {

    override suspend fun deleteBuildData(groupId: String, appId: String, buildId: String, user: User?) {
        transaction {
            if (!buildRepository.existsById(groupId, appId, buildId)) {
                throw InvalidParameters("Build not found for $buildId")
            }
            coverageRepository.deleteAllByBuildId(groupId, appId, buildId)
            instanceRepository.deleteAllByBuildId(groupId, appId, buildId)
            methodRepository.deleteAllByBuildId(groupId, appId, buildId)
            testSessionBuildRepository.deleteAllByBuildId(groupId, appId, buildId)
            buildRepository.deleteByBuildId(groupId, appId, buildId)
            scheduler.triggerJob(deleteMetricsDataJobKey, getBuildDataDeletionDataMap(groupId, appId, buildId))
        }
    }

    override suspend fun deleteTestSessionData(groupId: String, testSessionId: String, user: User?) {
        transaction {
            if (!testSessionRepository.existsById(groupId, testSessionId)) {
                throw InvalidParameters("Test Session not found for $testSessionId")
            }
            coverageRepository.deleteAllByTestSessionId(groupId, testSessionId)
            testLaunchRepository.deleteAllByTestSessionId(groupId, testSessionId)
            testSessionBuildRepository.deleteAllByTestSessionId(groupId, testSessionId)
            testSessionRepository.deleteByTestSessionId(groupId, testSessionId)
            scheduler.triggerJob(deleteMetricsDataJobKey, getTestDataDeletionDataMap(groupId, testSessionId))
        }
    }

    override suspend fun saveMethodIgnoreRule(rulePayload: MethodIgnoreRulePayload) {
        val rule = MethodIgnoreRule(
            groupId = rulePayload.groupId,
            appId = rulePayload.appId,
            namePattern = rulePayload.namePattern,
            classnamePattern = rulePayload.classnamePattern,
        )
        transaction {
            methodIgnoreRuleRepository.create(rule)
        }
    }

    override suspend fun getAllMethodIgnoreRules(): List<MethodIgnoreRuleView> {
        return transaction {
            methodIgnoreRuleRepository.getAll()
        }
    }

    override suspend fun deleteMethodIgnoreRuleById(ruleId: Int) {
        transaction {
            methodIgnoreRuleRepository.deleteById(ruleId)
        }
    }

    override suspend fun saveTestLaunchCoverageRequest(groupId: String, testSessionId: String, testDefinitionId: String?) {
        transaction {
            testLaunchCoverageRequestRepository.upsert(groupId, testSessionId, testDefinitionId)
        }
    }

    override suspend fun deleteTestLaunchCoverageRequest(groupId: String, testSessionId: String, testDefinitionId: String?) {
        transaction {
            testLaunchCoverageRequestRepository.delete(groupId, testSessionId, testDefinitionId)
        }
    }
}

