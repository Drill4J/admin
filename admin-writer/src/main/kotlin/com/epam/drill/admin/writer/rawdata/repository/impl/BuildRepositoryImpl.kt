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
import org.jetbrains.exposed.sql.insert

class BuildRepositoryImpl: BuildRepository {
    override fun create(build: Build) {
        BuildTable.insert {
            it[id] = build.id
            it[groupId] = build.groupId
            it[appId] = build.appId
            it[commitSha] = build.commitSha
            it[buildVersion] = build.buildVersion
            it[instanceId] = build.instanceId
            it[branch] = build.branch
            it[commitDate] = build.commitDate
            it[commitAuthor] = build.commitAuthor
            it[commitTags] = build.commitTags
        }
    }

}