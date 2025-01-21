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

import com.epam.drill.admin.writer.rawdata.entity.GroupSettings
import com.epam.drill.admin.writer.rawdata.repository.BuildRepository
import com.epam.drill.admin.writer.rawdata.repository.GroupSettingsRepository
import com.epam.drill.admin.writer.rawdata.route.payload.GroupSettingsPayload
import com.epam.drill.admin.writer.rawdata.service.SettingsService
import com.epam.drill.admin.writer.rawdata.views.GroupSettingsView
import org.jetbrains.exposed.sql.transactions.transaction

class SettingsServiceImpl(
    private val groupSettingsRepository: GroupSettingsRepository
) : SettingsService {

    override suspend fun getGroupSettings(groupId: String) = transaction {
        groupSettingsRepository.getByGroupId(groupId).let { settings ->
            GroupSettingsView(
                retentionPeriodDays = settings?.retentionPeriodDays
            )
        }
    }

    override suspend fun saveGroupSettings(groupId: String, payload: GroupSettingsPayload) = transaction {
        groupSettingsRepository.save(
            GroupSettings(
                groupId = groupId,
                retentionPeriodDays = payload.retentionPeriodDays
            )
        )
    }

    override suspend fun clearGroupSettings(groupId: String) = transaction {
        groupSettingsRepository.deleteByGroupId(groupId)
    }
}