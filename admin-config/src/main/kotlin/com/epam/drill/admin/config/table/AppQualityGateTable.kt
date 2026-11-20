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

object AppQualityGatesTable : Table("config.app_quality_gates") {
    val groupId = varchar("group_id", 255)
    val appId = varchar("app_id", 255)
    val codeCoverageTarget = decimal("code_coverage_target_percentage", 5, 2).nullable()
    val methodCoverageTarget = decimal("method_coverage_target_percentage", 5, 2).nullable()
    val testsPassedTarget = decimal("tests_passed_target_percentage", 5, 2).nullable()
    val changeCoverageTarget = decimal("change_coverage_target_percentage", 5, 2).nullable()
    val changeMethodsCoverageTarget = decimal("change_methods_coverage_target_percentage", 5, 2).nullable()
    val impactedTestsPassedTarget = decimal("impacted_tests_passed_target_percentage", 5, 2).nullable()
    override val primaryKey = PrimaryKey(groupId, appId)
}
