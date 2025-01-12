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
import com.epam.drill.admin.writer.rawdata.views.BuildView
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

class BuildRepositoryImpl: BuildRepository {
    override fun create(build: Build) {
        BuildTable.upsert() {
            it[id] = build.id
            it[groupId] = build.groupId
            it[appId] = build.appId
            it[commitSha] = build.commitSha
            it[buildVersion] = build.buildVersion
            it[instanceId] = build.instanceId
            it[branch] = build.branch
            it[commitDate] = build.commitDate
            it[commitAuthor] = build.commitAuthor
            it[commitMessage] = build.commitMessage
        }
    }
    override fun existsById(buildId: String): Boolean {
        return BuildTable.selectAll().where { BuildTable.id eq buildId }.any()
    }

    override fun getByBranch(branch: String): List<BuildView> {
        return BuildTable.selectAll().where { BuildTable.branch eq branch }.map {
            BuildView(
                id = it[BuildTable.id].value,
                groupId = it[BuildTable.groupId],
                appId = it[BuildTable.appId],
                commitSha = it[BuildTable.commitSha],
                buildVersion = it[BuildTable.buildVersion],
                branch = it[BuildTable.branch],
                instanceId = it[BuildTable.instanceId],
                commitDate = it[BuildTable.commitDate],
                commitMessage = it[BuildTable.commitMessage],
                commitAuthor = it[BuildTable.commitAuthor],
            )
        }
    }
}