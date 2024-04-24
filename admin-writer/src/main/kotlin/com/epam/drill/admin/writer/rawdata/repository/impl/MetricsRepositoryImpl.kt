package com.epam.drill.admin.writer.rawdata.repository.impl

import com.epam.drill.admin.writer.rawdata.config.executeQuery
import com.epam.drill.admin.writer.rawdata.repository.MetricsRepository
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.transactions.transaction

class MetricsRepositoryImpl : MetricsRepository {
    override fun getRisksByBranchDiff(
        groupId: String,
        agentId: String,
        currentBranch: String,
        currentVcsRef: String,
        baseBranch: String,
        baseVcsRef: String
    ): List<JsonObject> = transaction {
        executeQuery(
            """
                SELECT * 
                  FROM raw_data.get_risks_by_branch_diff(?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            groupId,
            agentId,
            currentVcsRef,
            currentBranch,
            baseBranch,
            baseVcsRef
        )
    }

    override fun getTotalCoverage(groupId: String, agentId: String, currentVcsRef: String): JsonObject = transaction {
        executeQuery(
            """
                SELECT raw_data.calculate_total_coverage_percent(?, ?, ?) as coverage
            """.trimIndent(),
            groupId,
            agentId,
            currentVcsRef
        ).first()
    }

    override fun getSummaryByBranchDiff(
        groupId: String,
        agentId: String,
        currentBranch: String,
        currentVcsRef: String,
        baseBranch: String,
        baseVcsRef: String
    ): JsonObject = transaction {
        executeQuery(
            """
                SELECT raw_data.calculate_total_coverage_percent(?, ?, ?) as coverage,
                       (SELECT count(*) 
                          FROM raw_data.get_risks_by_branch_diff(?, ?, ?, ?, ?, ?)) as risks
            """.trimIndent(),

            groupId,
            agentId,
            currentVcsRef,

            groupId,
            agentId,
            currentVcsRef,
            currentBranch,
            baseBranch,
            baseVcsRef
        ).first()
    }
}