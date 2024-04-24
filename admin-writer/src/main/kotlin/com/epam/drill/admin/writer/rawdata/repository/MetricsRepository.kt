package com.epam.drill.admin.writer.rawdata.repository

import kotlinx.serialization.json.JsonObject

interface MetricsRepository {

    fun getRisksByBranchDiff(
        groupId: String,
        agentId: String,
        currentBranch: String,
        currentVcsRef: String,
        baseBranch: String,
        baseVcsRef: String
    ): List<JsonObject>

    fun getTotalCoverage(
        groupId: String,
        agentId: String,
        currentVcsRef: String
    ): JsonObject

    fun getSummaryByBranchDiff(
        groupId: String,
        agentId: String,
        currentBranch: String,
        currentVcsRef: String,
        baseBranch: String,
        baseVcsRef: String
    ): JsonObject
}