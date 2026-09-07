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
import com.epam.drill.admin.writer.rawdata.repository.RawMethodBrowseRepository
import com.epam.drill.admin.writer.rawdata.service.RawMethodBrowseService
import org.jetbrains.exposed.sql.transactions.transaction

class RawMethodBrowseServiceImpl(
    private val repository: RawMethodBrowseRepository,
) : RawMethodBrowseService {
    override suspend fun getTree(groupId: String, appId: String, buildId: String) = transaction {
        buildRawMethodTree(repository.getMethodLeaves(groupId, appId, buildId))
    }

    override suspend fun getMethods(
        groupId: String,
        appId: String,
        buildId: String,
        className: String,
        page: Int,
        pageSize: Int,
    ) = transaction {
        validatePaging(page, pageSize)
        repository.getMethods(groupId, appId, buildId, className, page, pageSize)
    }

    private fun validatePaging(page: Int, pageSize: Int) {
        if (page < 1) throw InvalidParameters("Field 'page' must be greater than 0")
        if (pageSize !in 1..500) throw InvalidParameters("Field 'pageSize' must be between 1 and 500")
    }
}
