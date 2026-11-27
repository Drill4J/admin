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
package com.epam.drill.admin.config.table

import org.jetbrains.exposed.sql.Table

object MilestoneQualityGateTable : Table("config.milestone_quality_gates") {
    val groupId = varchar("group_id", 255)
    val trackId = varchar("track_id", 255)
    val milestoneId = varchar("milestone_id", 255)
    val overallCoverageTarget = decimal("overall_coverage_target_percentage", 5, 2).nullable()
    val overallMethodsCoverageTarget = decimal("overall_methods_coverage_target_percentage", 5, 2).nullable()
    val overallPassedTestsTarget = decimal("overall_passed_tests_target_percentage", 5, 2).nullable()
    val impactCoverageTarget = decimal("impact_coverage_target_percentage", 5, 2).nullable()
    val impactMethodsCoverageTarget = decimal("impact_methods_coverage_target_percentage", 5, 2).nullable()
    val impactPassedTestsTarget = decimal("impact_passed_tests_target_percentage", 5, 2).nullable()
    override val primaryKey = PrimaryKey(groupId, trackId, milestoneId)
}
