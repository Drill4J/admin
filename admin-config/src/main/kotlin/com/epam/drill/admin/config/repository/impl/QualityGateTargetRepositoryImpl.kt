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
package com.epam.drill.admin.config.repository.impl

import com.epam.drill.admin.config.model.ImpactQualityGateTargets
import com.epam.drill.admin.config.model.OverallQualityGateTargets
import com.epam.drill.admin.config.repository.QualityGateTargetRepository
import com.epam.drill.admin.config.table.MilestoneQualityGateTable
import org.jetbrains.exposed.sql.upsert

class QualityGateTargetRepositoryImpl : QualityGateTargetRepository {

    override fun upsertOverall(
        groupId: String,
        trackId: String,
        milestoneId: String,
        targets: OverallQualityGateTargets
    ) {
        MilestoneQualityGateTable.upsert(
            MilestoneQualityGateTable.groupId,
            MilestoneQualityGateTable.trackId,
            MilestoneQualityGateTable.milestoneId,
            onUpdateExclude = listOf(
                MilestoneQualityGateTable.impactCoverageTarget,
                MilestoneQualityGateTable.impactMethodsCoverageTarget,
                MilestoneQualityGateTable.impactPassedTestsTarget
            )
        ) {
            it[this.groupId] = groupId
            it[this.trackId] = trackId
            it[this.milestoneId] = milestoneId
            it[overallCoverageTarget] = targets.coverageTargetPercentage?.toBigDecimal()
            it[overallMethodsCoverageTarget] = targets.methodsCoverageTargetPercentage?.toBigDecimal()
            it[overallPassedTestsTarget] = targets.passedTestsTargetPercentage?.toBigDecimal()
        }
    }

    override fun upsertImpact(
        groupId: String,
        trackId: String,
        milestoneId: String,
        targets: ImpactQualityGateTargets
    ) {
        MilestoneQualityGateTable.upsert(
            MilestoneQualityGateTable.groupId,
            MilestoneQualityGateTable.trackId,
            MilestoneQualityGateTable.milestoneId,
            onUpdateExclude = listOf(
                MilestoneQualityGateTable.overallCoverageTarget,
                MilestoneQualityGateTable.overallMethodsCoverageTarget,
                MilestoneQualityGateTable.overallPassedTestsTarget
            )
        ) {
            it[this.groupId] = groupId
            it[this.trackId] = trackId
            it[this.milestoneId] = milestoneId
            it[impactCoverageTarget] = targets.coverageTargetPercentage?.toBigDecimal()
            it[impactMethodsCoverageTarget] = targets.methodsCoverageTargetPercentage?.toBigDecimal()
            it[impactPassedTestsTarget] = targets.passedTestsTargetPercentage?.toBigDecimal()
        }
    }
}
