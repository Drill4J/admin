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
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig.transaction
import com.epam.drill.admin.writer.rawdata.repository.BuildRepository
import com.epam.drill.admin.writer.rawdata.repository.CoverageRepository
import com.epam.drill.admin.writer.rawdata.repository.InstanceRepository
import com.epam.drill.admin.writer.rawdata.repository.MethodRepository
import com.epam.drill.admin.writer.rawdata.repository.TestLaunchRepository
import com.epam.drill.admin.writer.rawdata.repository.TestSessionBuildRepository
import com.epam.drill.admin.writer.rawdata.repository.TestSessionRepository
import com.epam.drill.admin.writer.rawdata.service.DataManagementService

class DataManagementServiceImpl(
    private val buildRepository: BuildRepository,
    private val testSessionRepository: TestSessionRepository,
    private val coverageRepository: CoverageRepository,
    private val instanceRepository: InstanceRepository,
    private val methodRepository: MethodRepository,
    private val testSessionBuildRepository: TestSessionBuildRepository,
    private val testLaunchRepository: TestLaunchRepository,
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
        }
    }
}

