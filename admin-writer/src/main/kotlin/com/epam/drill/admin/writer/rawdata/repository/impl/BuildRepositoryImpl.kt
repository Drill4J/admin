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
import com.epam.drill.admin.writer.rawdata.repository.BuildRepository
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDate

class BuildRepositoryImpl: BuildRepository {
    override fun create(build: Build) {
        BuildTable.upsert(
            onUpdateExclude = listOf(BuildTable.createdAt),
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
    override fun existsById(buildId: String): Boolean {
        return BuildTable.selectAll().where { BuildTable.id eq buildId }.any()
    }

    override fun deleteAllCreatedBefore(groupId: String, createdBefore: LocalDate) {
        BuildTable.deleteWhere { (BuildTable.groupId eq groupId) and (BuildTable.updatedAt less createdBefore.atStartOfDay()) }
    }
}