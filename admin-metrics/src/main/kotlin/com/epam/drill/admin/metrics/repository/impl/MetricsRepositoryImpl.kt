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
package com.epam.drill.admin.metrics.repository.impl

import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig.transaction
import com.epam.drill.admin.metrics.config.executeQueryReturnMap
import com.epam.drill.admin.metrics.repository.MetricsRepository

class MetricsRepositoryImpl : MetricsRepository {

    override suspend fun buildExists(
        buildId: String
    ): Boolean = transaction {
        val x = executeQueryReturnMap(
            """
                SELECT raw_data.check_build_exists(?)
                """.trimIndent(),
            buildId
        )
        x[0]["check_build_exists"] == true
    }

    override suspend fun getBuildDiffReport(
        buildId: String,
        baselineBuildId: String,
        coverageThreshold: Double
    ) = transaction {
        executeQueryReturnMap(
            """
                    WITH 
                    Risks AS (
                        SELECT * 
                        FROM  raw_data.get_build_risks_accumulated_coverage(?, ?)	
                    ),
                    RecommendedTests AS (
                        SELECT *
                        FROM raw_data.get_recommended_tests(?, ?)
                    )	
                    SELECT 
                        (SELECT count(*) FROM Risks WHERE __risk_type = 'new') as changes_new_methods,
                        (SELECT count(*) FROM Risks WHERE __risk_type = 'modified') as changes_modified_methods,
                        (SELECT count(*) FROM Risks) as total_changes,
                        (SELECT count(*) FROM Risks WHERE __probes_coverage_ratio > 0) as tested_changes,
                        (SELECT CAST(SUM(__covered_probes) AS FLOAT) / SUM(__probes_count) FROM Risks) as coverage,
                        (SELECT count(*) FROM RecommendedTests) as recommended_tests
                """.trimIndent(),
            buildId,
            baselineBuildId,
            buildId,
            baselineBuildId,
            coverageThreshold
        ).first() as Map<String, String>
    }

}