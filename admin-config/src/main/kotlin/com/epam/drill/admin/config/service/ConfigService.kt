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
package com.epam.drill.admin.config.service

import com.epam.drill.admin.config.model.AppImpactGateTargets
import com.epam.drill.admin.config.model.AppOverallGateTargets
import com.epam.drill.admin.config.model.AppQualityGateCount
import com.epam.drill.admin.config.model.ImpactQualityGateTargets
import com.epam.drill.admin.config.model.MilestoneListItem
import com.epam.drill.admin.config.model.MilestonePayload
import com.epam.drill.admin.config.model.OverallQualityGateTargets
import com.epam.drill.admin.config.model.ReleaseTrackView

interface ConfigService {
    suspend fun createTrack(groupId: String, trackId: String)
    suspend fun getTracks(groupId: String): List<ReleaseTrackView>
    suspend fun getMilestones(groupId: String, trackId: String): List<MilestoneListItem>
    suspend fun saveMilestone(groupId: String, trackId: String, payload: MilestonePayload)
    suspend fun saveMilestoneOverallTargets(
        groupId: String,
        trackId: String,
        milestoneId: String,
        targets: OverallQualityGateTargets
    )
    suspend fun saveMilestoneImpactTargets(
        groupId: String,
        trackId: String,
        milestoneId: String,
        targets: ImpactQualityGateTargets
    )
    suspend fun getAppOverallTargets(groupId: String, appId: String): AppOverallGateTargets
    suspend fun saveAppOverallTargets(groupId: String, appId: String, targets: AppOverallGateTargets)
    suspend fun getAppImpactTargets(groupId: String, appId: String): AppImpactGateTargets
    suspend fun saveAppImpactTargets(groupId: String, appId: String, targets: AppImpactGateTargets)
}
