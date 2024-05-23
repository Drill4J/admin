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

import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig.transaction
import com.epam.drill.admin.writer.rawdata.entity.*
import com.epam.drill.admin.writer.rawdata.repository.*
import com.epam.drill.admin.writer.rawdata.route.payload.*
import com.epam.drill.admin.writer.rawdata.service.RawDataWriter

private const val EXEC_DATA_BATCH_SIZE = 100

class RawDataServiceImpl(
    private val instanceRepository: InstanceRepository,
    private val coverageRepository: CoverageRepository,
    private val testMetadataRepository: TestMetadataRepository,
    private val methodRepository: MethodRepository,
    private val buildRepository: BuildRepository
) : RawDataWriter {

    override suspend fun saveBuild(buildPayload: BuildPayload) {
        val build = Build(
            id = generateBuildId(
                buildPayload.groupId,
                buildPayload.appId,
                "",
                buildPayload.commitSha,
                buildPayload.buildVersion
            ),
            groupId = buildPayload.groupId,
            appId = buildPayload.appId,
            commitSha = buildPayload.commitSha,
            buildVersion = buildPayload.buildVersion,
            branch = buildPayload.branch,
            commitDate = buildPayload.commitDate,
            commitMessage = buildPayload.commitMessage,
            commitAuthor = buildPayload.commitAuthor,
            commitTags = buildPayload.commitTags,
        )
        transaction {
            buildRepository.create(build)
        }
    }

    override suspend fun saveInstance(instancePayload: InstancePayload) {
        val instance = Instance(
            id = instancePayload.instanceId,
            buildId = generateBuildId(
                instancePayload.groupId,
                instancePayload.appId,
                instancePayload.instanceId,
                instancePayload.commitSha,
                instancePayload.buildVersion
            )
        )
        transaction {
            instanceRepository.create(instance)
        }
    }

    override suspend fun saveMethods(methodsPayload: MethodsPayload) {
        methodsPayload.methods.map { method ->
            Method(
                buildId = generateBuildId(
                    methodsPayload.groupId,
                    methodsPayload.appId,
                    methodsPayload.instanceId,
                    methodsPayload.commitSha,
                    methodsPayload.buildVersion
                ),
                classname = method.classname,
                name = method.name,
                params = method.params,
                returnType = method.returnType,
                probesCount = method.probesCount,
                probesStartPos = method.probesStartPos,
                bodyChecksum = method.bodyChecksum
            )
        }
        .let { dataToInsert ->
            transaction {
                methodRepository.createMany(dataToInsert)
            }
        }
    }

    override suspend fun saveCoverage(coveragePayload: CoveragePayload) {
        coveragePayload.coverage.map { coverage ->
                Coverage(
                    instanceId = coveragePayload.instanceId,
                    classname = coverage.classname,
                    testId = coverage.testId,
                    probes = coverage.probes
                )
            }
            .chunked(EXEC_DATA_BATCH_SIZE)
            .forEach { data ->
                transaction {
                    coverageRepository.createMany(data)
                }
            }
    }

    override suspend fun saveTestMetadata(testsPayload: AddTestsPayload) {
        testsPayload.tests.map { test ->
            TestMetadata(
                testId = test.id,
                name = test.details.testName,
                type = "placeholder"
            )
        }.let { dataToInsert ->
            transaction {
                testMetadataRepository.createMany(dataToInsert)
            }
        }
    }

    private fun generateBuildId(
        groupId: String,
        appId: String,
        instanceId: String = "",
        commitSha: String = "",
        buildVersion: String = "",
    ): String {
        require(groupId.isNotBlank()) { "groupId cannot be empty or blank" }
        require(appId.isNotBlank()) { "appId cannot be empty or blank" }
        require(instanceId.isNotBlank() || commitSha.isNotBlank() || buildVersion.isNotBlank()) {
            "provide at least one of the following: instanceId, commitSha or buildVersion"
        }

        val buildIdElements = mutableListOf(groupId, appId)
        val firstNotBlank = listOf(buildVersion, commitSha, instanceId).first { it.isNotBlank() }
        buildIdElements.add(firstNotBlank)
        return buildIdElements.joinToString(":")
    }
}
