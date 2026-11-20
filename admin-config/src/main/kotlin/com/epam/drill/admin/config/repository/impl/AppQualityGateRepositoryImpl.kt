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

import com.epam.drill.admin.config.configExecuteQuery
import com.epam.drill.admin.config.model.AppImpactGateTargets
import com.epam.drill.admin.config.model.AppOverallGateTargets
import com.epam.drill.admin.config.repository.AppQualityGateRepository
import com.epam.drill.admin.config.table.AppQualityGatesTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.upsert

class AppQualityGateRepositoryImpl : AppQualityGateRepository {

    override fun getOverall(groupId: String, appId: String): AppOverallGateTargets {
        return AppQualityGatesTable.selectAll()
            .where { (AppQualityGatesTable.groupId eq groupId) and (AppQualityGatesTable.appId eq appId) }
            .singleOrNull()
            ?.let { row ->
                AppOverallGateTargets(
                    codeCoveragePercentage = row[AppQualityGatesTable.codeCoverageTarget]?.toDouble(),
                    methodCoveragePercentage = row[AppQualityGatesTable.methodCoverageTarget]?.toDouble(),
                    testsPassedPercentage = row[AppQualityGatesTable.testsPassedTarget]?.toDouble()
                )
            } ?: AppOverallGateTargets(null, null, null)
    }

    override fun upsertOverall(groupId: String, appId: String, targets: AppOverallGateTargets) {
        AppQualityGatesTable.upsert(
            AppQualityGatesTable.groupId,
            AppQualityGatesTable.appId,
            onUpdateExclude = listOf(
                AppQualityGatesTable.changeCoverageTarget,
                AppQualityGatesTable.changeMethodsCoverageTarget,
                AppQualityGatesTable.impactedTestsPassedTarget
            )
        ) {
            it[this.groupId] = groupId
            it[this.appId] = appId
            it[codeCoverageTarget] = targets.codeCoveragePercentage?.toBigDecimal()
            it[methodCoverageTarget] = targets.methodCoveragePercentage?.toBigDecimal()
            it[testsPassedTarget] = targets.testsPassedPercentage?.toBigDecimal()
        }
    }

    override fun getImpact(groupId: String, appId: String): AppImpactGateTargets {
        return AppQualityGatesTable.selectAll()
            .where { (AppQualityGatesTable.groupId eq groupId) and (AppQualityGatesTable.appId eq appId) }
            .singleOrNull()
            ?.let { row ->
                AppImpactGateTargets(
                    changeCoveragePercentage = row[AppQualityGatesTable.changeCoverageTarget]?.toDouble(),
                    changeMethodsCoveragePercentage = row[AppQualityGatesTable.changeMethodsCoverageTarget]?.toDouble(),
                    impactedTestsPassedPercentage = row[AppQualityGatesTable.impactedTestsPassedTarget]?.toDouble()
                )
            } ?: AppImpactGateTargets(null, null, null)
    }

    override fun upsertImpact(groupId: String, appId: String, targets: AppImpactGateTargets) {
        AppQualityGatesTable.upsert(
            AppQualityGatesTable.groupId,
            AppQualityGatesTable.appId,
            onUpdateExclude = listOf(
                AppQualityGatesTable.codeCoverageTarget,
                AppQualityGatesTable.methodCoverageTarget,
                AppQualityGatesTable.testsPassedTarget
            )
        ) {
            it[this.groupId] = groupId
            it[this.appId] = appId
            it[changeCoverageTarget] = targets.changeCoveragePercentage?.toBigDecimal()
            it[changeMethodsCoverageTarget] = targets.changeMethodsCoveragePercentage?.toBigDecimal()
            it[impactedTestsPassedTarget] = targets.impactedTestsPassedPercentage?.toBigDecimal()
        }
    }

    override fun countAppsWithAllOverallTargets(groupId: String): Int {
        return TransactionManager.current().configExecuteQuery(
            """
            SELECT COUNT(*) AS cnt FROM config.app_quality_gates
            WHERE group_id = ?
              AND code_coverage_target_percentage IS NOT NULL
              AND method_coverage_target_percentage IS NOT NULL
              AND tests_passed_target_percentage IS NOT NULL
            """.trimIndent(),
            groupId
        ).firstOrNull()?.let { (it["cnt"] as? Long)?.toInt() } ?: 0
    }

    override fun countAppsWithAllImpactTargets(groupId: String): Int {
        return TransactionManager.current().configExecuteQuery(
            """
            SELECT COUNT(*) AS cnt FROM config.app_quality_gates
            WHERE group_id = ?
              AND change_coverage_target_percentage IS NOT NULL
              AND change_methods_coverage_target_percentage IS NOT NULL
              AND impacted_tests_passed_target_percentage IS NOT NULL
            """.trimIndent(),
            groupId
        ).firstOrNull()?.let { (it["cnt"] as? Long)?.toInt() } ?: 0
    }

    override fun countTotalApps(groupId: String): Int {
        return TransactionManager.current().configExecuteQuery(
            "SELECT COUNT(DISTINCT app_id) AS cnt FROM metrics.builds WHERE group_id = ?",
            groupId
        ).firstOrNull()?.let { (it["cnt"] as? Long)?.toInt() } ?: 0
    }
}
