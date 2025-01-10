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
import com.epam.drill.admin.writer.rawdata.views.MethodIgnoreRuleView
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime

private const val EXEC_DATA_BATCH_SIZE = 100

class RawDataServiceImpl(
    private val instanceRepository: InstanceRepository,
    private val coverageRepository: CoverageRepository,
    private val testMetadataRepository: TestMetadataRepository,
    private val methodRepository: MethodRepository,
    private val buildRepository: BuildRepository,
    private val testSessionRepository: TestSessionRepository,
    private val methodIgnoreRuleRepository: MethodIgnoreRuleRepository
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
            buildId = buildId,
            envId = instancePayload.envId
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
                signature = listOf(
                    method.classname,
                    method.name,
                    method.params,
                    method.returnType
                ).joinToString(":"),
                annotations = method.annotations,
                classAnnotations = method.classAnnotations
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
                launch = TestLaunch(
                    groupId = test.groupId,
                    id = test.id,
                    testDefinitionId = test.testDefinitionId,
                    testSessionId = testsPayload.sessionId,
                    result = test.result.toString()
                ),
                definition = TestDefinition(
                    groupId = test.groupId,
                    id = test.testDefinitionId,
                    type = "placeholder", // TODO replace once it's implemented on autotest agent
                    runner = test.details.engine,
                    name = test.details.testName,
                    path = test.details.path,
                    tags = test.details.labels.map { x -> x.value }.joinToString(",")
                )
            )
        }.let { dataToInsert ->
            transaction {
                testMetadataRepository.createMany(dataToInsert)
            }
        }
    }

    override suspend fun saveTestSession(sessionPayload: SessionPayload) {
        val testSession = TestSession(
            id = sessionPayload.id,
            groupId = sessionPayload.groupId,
            testTaskId = sessionPayload.testTaskId,
            startedAt = sessionPayload.startedAt.toLocalDateTime(TimeZone.UTC).toJavaLocalDateTime()
        )
        transaction {
            testSessionRepository.create(testSession)
        }
    }

    override suspend fun saveMethodIgnoreRule(rulePayload: MethodIgnoreRulePayload) {
        val rule = MethodIgnoreRule(
            groupId = rulePayload.groupId,
            appId = rulePayload.appId,
            namePattern = rulePayload.namePattern,
            classnamePattern = rulePayload.classnamePattern,
            annotationsPattern = rulePayload.annotationsPattern,
            classAnnotationsPattern = rulePayload.classAnnotationsPattern
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
