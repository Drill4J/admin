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
package com.epam.drill.admin.config.service.impl

import com.epam.drill.admin.common.exception.ConflictException
import com.epam.drill.admin.config.ConfigDatabaseConfig.transaction
import com.epam.drill.admin.config.model.AppImpactGateTargets
import com.epam.drill.admin.config.model.AppOverallGateTargets
import com.epam.drill.admin.config.model.AppQualityGateCount
import com.epam.drill.admin.config.model.ImpactQualityGateTargets
import com.epam.drill.admin.config.model.MilestoneLatestView
import com.epam.drill.admin.config.model.MilestoneListItem
import com.epam.drill.admin.config.model.MilestonePayload
import com.epam.drill.admin.config.model.OverallQualityGateTargets
import com.epam.drill.admin.config.model.ReleaseTrackLight
import com.epam.drill.admin.config.model.ReleaseTrackView
import com.epam.drill.admin.config.repository.AppQualityGateRepository
import com.epam.drill.admin.config.repository.MilestoneRepository
import com.epam.drill.admin.config.repository.QualityGateTargetRepository
import com.epam.drill.admin.config.repository.ReleaseTrackRepository
import com.epam.drill.admin.config.service.ConfigService
import kotlinx.datetime.toKotlinLocalDateTime

class ConfigServiceImpl(
    private val releaseTrackRepository: ReleaseTrackRepository,
    private val milestoneRepository: MilestoneRepository,
    private val qualityGateTargetRepository: QualityGateTargetRepository,
    private val appQualityGateRepository: AppQualityGateRepository
) : ConfigService {

    override suspend fun createTrack(groupId: String, trackId: String) = transaction {
        if (releaseTrackRepository.existsByTrackId(groupId, trackId)) {
            throw ConflictException("Release track '$trackId' already exists in group '$groupId'")
        }
        releaseTrackRepository.insert(groupId, trackId)
    }

    override suspend fun getTracks(groupId: String): List<ReleaseTrackView> = transaction {
        val tracks = releaseTrackRepository.getList(groupId)
        tracks.map { track ->
            val milestones = milestoneRepository.getList(groupId, track.trackId)
            val latest = milestones.firstOrNull()
            val baseline = if (milestones.size > 1) milestones[1] else null
            ReleaseTrackView(
                trackId = track.trackId,
                createdAt = track.createdAt.toKotlinLocalDateTime(),
                milestoneCount = milestones.size,
                latest = latest?.let {
                    MilestoneLatestView(
                        milestoneId = it.milestoneId,
                        createdAt = it.createdAt.toKotlinLocalDateTime(),
                        baselineMilestoneId = baseline?.milestoneId
                    )
                }
            )
        }
    }

    override suspend fun getMilestones(groupId: String, trackId: String): List<MilestoneListItem> = transaction {
        milestoneRepository.getList(groupId, trackId).map { row ->
            MilestoneListItem(
                milestoneId = row.milestoneId,
                createdAt = row.createdAt.toKotlinLocalDateTime()
            )
        }
    }

    override suspend fun saveMilestone(groupId: String, trackId: String, payload: MilestonePayload) = transaction {
        require(payload.apps.isNotEmpty()) { "At least one app is required" }
        val validAppIds = milestoneRepository.getTrackAppIds(groupId, trackId)
        val invalidAppIds = payload.apps.map { it.appId }.filter { it !in validAppIds }
        require(invalidAppIds.isEmpty()) {
            "Apps not registered on track '$trackId': ${invalidAppIds.joinToString()}"
        }
        milestoneRepository.upsertMilestone(groupId, trackId, payload.milestoneId)
        milestoneRepository.deleteApps(groupId, trackId, payload.milestoneId)
        milestoneRepository.insertApps(groupId, trackId, payload.milestoneId, payload.apps)
    }

    override suspend fun saveMilestoneOverallTargets(
        groupId: String,
        trackId: String,
        milestoneId: String,
        targets: OverallQualityGateTargets
    ) = transaction {
        qualityGateTargetRepository.upsertOverall(groupId, trackId, milestoneId, targets)
    }

    override suspend fun saveMilestoneImpactTargets(
        groupId: String,
        trackId: String,
        milestoneId: String,
        targets: ImpactQualityGateTargets
    ) = transaction {
        qualityGateTargetRepository.upsertImpact(groupId, trackId, milestoneId, targets)
    }

    override suspend fun getAppOverallTargets(groupId: String, appId: String): AppOverallGateTargets = transaction {
        appQualityGateRepository.getOverall(groupId, appId)
    }

    override suspend fun saveAppOverallTargets(groupId: String, appId: String, targets: AppOverallGateTargets) = transaction {
        appQualityGateRepository.upsertOverall(groupId, appId, targets)
    }

    override suspend fun getAppImpactTargets(groupId: String, appId: String): AppImpactGateTargets = transaction {
        appQualityGateRepository.getImpact(groupId, appId)
    }

    override suspend fun saveAppImpactTargets(groupId: String, appId: String, targets: AppImpactGateTargets) = transaction {
        appQualityGateRepository.upsertImpact(groupId, appId, targets)
    }
}
