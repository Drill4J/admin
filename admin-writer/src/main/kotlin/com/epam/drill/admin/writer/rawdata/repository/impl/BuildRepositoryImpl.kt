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
package com.epam.drill.admin.writer.rawdata.repository.impl

import com.epam.drill.admin.writer.rawdata.entity.Build
import com.epam.drill.admin.writer.rawdata.entity.BuildValidationStatus
import com.epam.drill.admin.writer.rawdata.repository.BuildRepository
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDate

class BuildRepositoryImpl : BuildRepository {
    override suspend fun saveBuildInfo(build: Build) {
        BuildTable.upsert(
            onUpdateExclude = listOf(
                BuildTable.createdAt,
            ),
        ) {
            it[id] = build.id
            it[groupId] = build.groupId
            it[appId] = build.appId
            it[commitSha] = build.commitSha
            it[buildVersion] = build.buildVersion
            it[instanceId] = build.instanceId
            it[branch] = build.branch
            it[committedAt] = build.commitDate
            it[commitAuthor] = build.commitAuthor
            it[commitMessage] = build.commitMessage
            it[updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime
        }
    }

    override suspend fun saveBuildId(build: Build) {
        BuildTable.upsert(
            onUpdateExclude = listOf(
                BuildTable.createdAt,
                BuildTable.branch,
                BuildTable.committedAt,
                BuildTable.commitAuthor,
                BuildTable.commitMessage,
                BuildTable.commitMessage,
                BuildTable.validationStatus,
                BuildTable.validatedAt,
                BuildTable.finalizedAt,
                BuildTable.agentVersion,
                BuildTable.agentEnv,
                BuildTable.agentParams,
            ),
        ) {
            it[id] = build.id
            it[groupId] = build.groupId
            it[appId] = build.appId
            it[commitSha] = build.commitSha
            it[buildVersion] = build.buildVersion
            it[instanceId] = build.instanceId
            it[agentVersion] = build.agentVersion
            it[agentEnv] = build.agentEnv
            it[agentParams] = build.agentParams
            it[updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime
        }
    }

    override suspend fun existsById(groupId: String, appId: String, buildId: String): Boolean {
        return BuildTable.selectAll().where {
            (BuildTable.groupId eq groupId) and
                    (BuildTable.appId eq appId) and
                    (BuildTable.id eq buildId)
        }.any()
    }

    override suspend fun deleteAllCreatedBefore(groupId: String, createdBefore: LocalDate) {
        BuildTable.deleteWhere { (BuildTable.groupId eq groupId) and (BuildTable.updatedAt less createdBefore.atStartOfDay()) }
    }

    override suspend fun deleteByBuildId(groupId: String, appId: String, buildId: String) {
        BuildTable.deleteWhere {
            (BuildTable.groupId eq groupId) and (BuildTable.appId eq appId) and (BuildTable.id eq buildId)
        }
    }

    override suspend fun getById(groupId: String, appId: String, buildId: String): Build? {
        return BuildTable.selectAll().where {
            (BuildTable.groupId eq groupId) and (BuildTable.appId eq appId) and (BuildTable.id eq buildId)
        }.map { it.toBuild() }.firstOrNull()
    }

    override suspend fun getStatus(groupId: String, appId: String, buildId: String): BuildValidationStatus? {
        return BuildTable.select(BuildTable.validationStatus)
            .where {
                (BuildTable.groupId eq groupId) and (BuildTable.appId eq appId) and (BuildTable.id eq buildId)
            }
            .map { it[BuildTable.validationStatus] }.firstOrNull()
            ?.let { BuildValidationStatus.valueOf(it) }
    }

    override suspend fun saveBuildFinalization(
        groupId: String,
        appId: String,
        buildId: String,
        methodsCount: Int,
        methodsChecksum: String,
    ) {
        BuildTable.upsert(
            onUpdateExclude = listOf(
                BuildTable.createdAt,
                BuildTable.branch,
                BuildTable.committedAt,
                BuildTable.commitAuthor,
                BuildTable.commitMessage,
                BuildTable.commitSha,
                BuildTable.buildVersion,
                BuildTable.instanceId,
            ),
        ) {
            it[id] = buildId
            it[BuildTable.groupId] = groupId
            it[BuildTable.appId] = appId
            it[BuildTable.methodsCount] = methodsCount
            it[BuildTable.methodsChecksum] = methodsChecksum
            it[BuildTable.validationStatus] = BuildValidationStatus.PENDING.name
            it[BuildTable.validatedAt] = null
            it[BuildTable.finalizedAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime
            it[BuildTable.updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime
        }
    }

    override suspend fun updateBuildStatus(
        groupId: String,
        appId: String,
        buildId: String,
        status: BuildValidationStatus,
    ) {
        BuildTable.update(
            where = {
                (BuildTable.groupId eq groupId) and (BuildTable.appId eq appId) and (BuildTable.id eq buildId)
            }
        ) {
            it[BuildTable.validationStatus] = status.name
            it[BuildTable.validatedAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime
            it[BuildTable.updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime
        }
    }

    override suspend fun findBuildsToRetry(limit: Int): List<Build> {
        return BuildTable.selectAll().where {
            BuildTable.validationStatus eq BuildValidationStatus.PENDING.name
        }.limit(limit).map { it.toBuild() }
    }

    private fun ResultRow.toBuild(): Build = Build(
        id = this[BuildTable.id].value,
        groupId = this[BuildTable.groupId],
        appId = this[BuildTable.appId],
        commitSha = this[BuildTable.commitSha],
        buildVersion = this[BuildTable.buildVersion],
        instanceId = this[BuildTable.instanceId],
        branch = this[BuildTable.branch],
        commitDate = this[BuildTable.committedAt],
        commitMessage = this[BuildTable.commitMessage],
        commitAuthor = this[BuildTable.commitAuthor],
        status = this[BuildTable.validationStatus]?.let { BuildValidationStatus.valueOf(it) },
        methodsCount = this[BuildTable.methodsCount],
        buildChecksum = this[BuildTable.methodsChecksum],
        finalizedAt = this[BuildTable.finalizedAt],
        validatedAt = this[BuildTable.validatedAt],
    )
}