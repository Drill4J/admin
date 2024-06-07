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
            // TODO generate build id with SQL function + TRIGGER on INSERT?
            id = generateBuildId(
                buildPayload.groupId,
                buildPayload.appId,
                "",
                buildPayload.commitSha,
                buildPayload.buildVersion
            ),
            groupId = buildPayload.groupId,
            appId = buildPayload.appId,
            instanceId = null,
            commitSha = buildPayload.commitSha,
            buildVersion = buildPayload.buildVersion,
            branch = buildPayload.branch,
            commitDate = buildPayload.commitDate,
            commitMessage = buildPayload.commitMessage,
            commitAuthor = buildPayload.commitAuthor
        )
        transaction {
            buildRepository.create(build)
        }
    }

    override suspend fun saveInstance(instancePayload: InstancePayload) {
        val buildId = generateBuildId(
            instancePayload.groupId,
            instancePayload.appId,
            instancePayload.instanceId,
            instancePayload.commitSha,
            instancePayload.buildVersion
        )
        val instance = Instance(
            id = instancePayload.instanceId,
            buildId = buildId
        )
        transaction {
            if (!buildRepository.existsById(buildId)) {
                val build = Build(
                    id = buildId,
                    groupId = instancePayload.groupId,
                    appId = instancePayload.appId,
                    instanceId = instancePayload.instanceId,
                    commitSha = instancePayload.commitSha,
                    buildVersion = instancePayload.buildVersion,
                    branch = null,
                    commitDate = null,
                    commitMessage = null,
                    commitAuthor = null
                )
                buildRepository.create(build)
            }
            instanceRepository.create(instance)
        }
    }

    override suspend fun saveMethods(methodsPayload: MethodsPayload) {
        methodsPayload.methods.map { method ->
            val buildId = generateBuildId(
                methodsPayload.groupId,
                methodsPayload.appId,
                methodsPayload.instanceId,
                methodsPayload.commitSha,
                methodsPayload.buildVersion
            )
            // TODO add validation for fields (we had issues with body_checksum)
            Method(
                // TODO concat in sql?
                //      id VARCHAR GENERATED ALWAYS AS (classname || ':' || name || etc ) STORED
                id = listOf(
                        buildId,
                        method.classname,
                        method.name,
                        method.params,
                        method.returnType
                    ).joinToString(":"),
                buildId = buildId,
                classname = method.classname,
                name = method.name,
                params = method.params,
                returnType = method.returnType,
                probesCount = method.probesCount,
                probesStartPos = method.probesStartPos,
                bodyChecksum = method.bodyChecksum,

                // TODO store checksum instead of actual string?
                //  pros: fixed length -> storage & perf
                //  cons: readability, api consumers might want to "know" about hashing algorithm
                //  solution: optimize as needed
                //      Case: test on N rows ( 1kk ? 10kk? ) and judge column size
                signature = listOf(
                    method.classname,
                    method.name,
                    method.params,
                    method.returnType
                ).joinToString(":")
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
                testDefinitionId = test.id,
                type = "placeholder", // TODO replace once user-defined value is implemented on autotest agent
                runner = test.details.engine,
                name = test.details.testName,
                path = test.details.path,
                result = test.result.toString()
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
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?
    ): String {
        require(groupId.isNotBlank()) { "groupId cannot be empty or blank" }
        require(appId.isNotBlank()) { "appId cannot be empty or blank" }
        require(!instanceId.isNullOrBlank() || !commitSha.isNullOrBlank() || !buildVersion.isNullOrBlank()) {
            "provide at least one of the following: instanceId, commitSha or buildVersion"
        }

        val buildIdElements = mutableListOf(groupId, appId)
        val firstNotBlank = listOf(buildVersion, commitSha, instanceId).first { !it.isNullOrBlank() }
        buildIdElements.add(firstNotBlank as String) // TODO think of better way to convince typesystem its not null
        return buildIdElements.joinToString(":")
    }
}
