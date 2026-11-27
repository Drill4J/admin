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
package com.epam.drill.admin.config.repository

import com.epam.drill.admin.config.model.AppImpactGateTargets
import com.epam.drill.admin.config.model.AppOverallGateTargets

interface AppQualityGateRepository {
    fun getOverall(groupId: String, appId: String): AppOverallGateTargets
    fun upsertOverall(groupId: String, appId: String, targets: AppOverallGateTargets)
    fun getImpact(groupId: String, appId: String): AppImpactGateTargets
    fun upsertImpact(groupId: String, appId: String, targets: AppImpactGateTargets)
    /** Count of apps that have all three overall targets set (non-null). */
    fun countAppsWithAllOverallTargets(groupId: String): Int
    /** Count of apps that have all three impact targets set (non-null). */
    fun countAppsWithAllImpactTargets(groupId: String): Int
    /** Count of distinct app_ids across all builds for the group. */
    fun countTotalApps(groupId: String): Int
}
