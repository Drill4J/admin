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
import com.epam.drill.admin.common.scheduler.getAppDataDeletionDataMap
import com.epam.drill.admin.common.scheduler.getGroupDataDeletionDataMap
import com.epam.drill.admin.common.scheduler.getBuildDataDeletionDataMap
import com.epam.drill.admin.common.scheduler.getTestSessionDataDeletionDataMap
import com.epam.drill.admin.common.scheduler.getTestProjectDataDeletionDataMap
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig.transaction
import com.epam.drill.admin.writer.rawdata.entity.MethodIgnoreRule
import com.epam.drill.admin.writer.rawdata.repository.BuildRepository
import com.epam.drill.admin.writer.rawdata.repository.CoverageRepository
import com.epam.drill.admin.writer.rawdata.repository.InstanceRepository
import com.epam.drill.admin.writer.rawdata.repository.MethodIgnoreRuleRepository
import com.epam.drill.admin.writer.rawdata.repository.MethodRepository
import com.epam.drill.admin.writer.rawdata.repository.TestDefinitionRepository
import com.epam.drill.admin.writer.rawdata.repository.TestLaunchCoverageRequestRepository
import com.epam.drill.admin.writer.rawdata.repository.TestLaunchRepository
import com.epam.drill.admin.writer.rawdata.repository.TestSessionBuildRepository
import com.epam.drill.admin.writer.rawdata.repository.TestSessionRepository
import com.epam.drill.admin.writer.rawdata.route.payload.MethodIgnoreRulePayload
import com.epam.drill.admin.writer.rawdata.service.DataManagementService
import com.epam.drill.admin.writer.rawdata.views.MethodIgnoreRulesPageView

class DataManagementServiceImpl(
    private val buildRepository: BuildRepository,
    private val testSessionRepository: TestSessionRepository,
    private val coverageRepository: CoverageRepository,
    private val instanceRepository: InstanceRepository,
    private val methodRepository: MethodRepository,
    private val testSessionBuildRepository: TestSessionBuildRepository,
    private val testLaunchRepository: TestLaunchRepository,
    private val testDefinitionRepository: TestDefinitionRepository,
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

    override suspend fun deleteGroupData(groupId: String, user: User?) {
        transaction {
            coverageRepository.deleteAllByGroupId(groupId)
            instanceRepository.deleteAllByGroupId(groupId)
            methodRepository.deleteAllByGroupId(groupId)
            testSessionBuildRepository.deleteAllByGroupId(groupId)
            buildRepository.deleteAllByGroupId(groupId)
            testDefinitionRepository.deleteAllByGroupId(groupId)
            testLaunchRepository.deleteAllByGroupId(groupId)
            testSessionRepository.deleteAllByGroupId(groupId)
            scheduler.triggerJob(deleteMetricsDataJobKey, getGroupDataDeletionDataMap(groupId))
        }
    }

    override suspend fun deleteAppData(groupId: String, appId: String, user: User?) {
        transaction {
            coverageRepository.deleteAllByAppId(groupId, appId)
            instanceRepository.deleteAllByAppId(groupId, appId)
            methodRepository.deleteAllByAppId(groupId, appId)
            testSessionBuildRepository.deleteAllByAppId(groupId, appId)
            buildRepository.deleteAllByAppId(groupId, appId)
            scheduler.triggerJob(deleteMetricsDataJobKey, getAppDataDeletionDataMap(groupId, appId))
        }
    }

    override suspend fun deleteTestProjectData(groupId: String, testProjectId: String, user: User?) {
        transaction {
            coverageRepository.deleteAllByTestProjectId(groupId, testProjectId)
            testLaunchRepository.deleteAllByTestProjectId(groupId, testProjectId)
            testSessionBuildRepository.deleteAllByTestProjectId(groupId, testProjectId)
            testSessionRepository.deleteAllByTestProjectId(groupId, testProjectId)
            testDefinitionRepository.deleteAllByTestProjectId(groupId, testProjectId)
            scheduler.triggerJob(deleteMetricsDataJobKey, getTestProjectDataDeletionDataMap(groupId, testProjectId))
        }
    }

    override suspend fun deleteTestSessionData(
        groupId: String,
        testProjectId: String?,
        testSessionId: String,
        user: User?
    ) {
        transaction {
            if (!testSessionRepository.existsById(groupId, testSessionId)) {
                throw InvalidParameters("Test Session not found for $testSessionId")
            }
            coverageRepository.deleteAllByTestSessionId(groupId, testSessionId)
            testLaunchRepository.deleteAllByTestSessionId(groupId, testProjectId, testSessionId)
            testSessionBuildRepository.deleteAllByTestSessionId(groupId, testSessionId)
            testSessionRepository.deleteByTestSessionId(groupId, testProjectId, testSessionId)
            scheduler.triggerJob(deleteMetricsDataJobKey, getTestSessionDataDeletionDataMap(groupId, testProjectId, testSessionId))
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

    override suspend fun getAllMethodIgnoreRules(
        groupId: String,
        appId: String,
        page: Int,
        pageSize: Int,
    ): MethodIgnoreRulesPageView {
        if (page < 1) throw InvalidParameters("Field 'page' must be greater than 0")
        if (pageSize !in 1..500) throw InvalidParameters("Field 'pageSize' must be between 1 and 500")
        return transaction {
            methodIgnoreRuleRepository.getAll(groupId, appId, page, pageSize)
        }
    }

    override suspend fun deleteMethodIgnoreRuleById(groupId: String, appId: String, ruleId: Int) {
        transaction {
            methodIgnoreRuleRepository.deleteById(groupId, appId, ruleId)
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

