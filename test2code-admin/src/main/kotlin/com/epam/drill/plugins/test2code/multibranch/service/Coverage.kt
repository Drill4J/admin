package com.epam.drill.plugins.test2code.multibranch.service

import com.epam.drill.plugins.test2code.multibranch.repository.RawDataRepositoryImpl

suspend fun versionCoverage(agentId: String, buildVersion: String) {
    val agent = RawDataRepositoryImpl.getAgentConfigs(agentId, buildVersion)
    val ast = RawDataRepositoryImpl.getAstEntities(agentId, buildVersion)
    val coverage = RawDataRepositoryImpl.getRawCoverageData(agentId, buildVersion)

    /* query to calculate coverage %
    SELECT
        class_name,
        --                         - 1 is required since we use last bit as original array end indicator
        (BIT_COUNT(BIT_OR(probes)) - 1) * 100.0 / (LENGTH(BIT_OR(probes)::VARBIT) - 1) AS set_bits_percentage,
        -- not important for final metrics
        COUNT(*) AS entry_count,
        BIT_COUNT(BIT_OR(probes)) AS set_bits_count,
        LENGTH(BIT_OR(probes)::VARBIT) AS result_length,
        BIT_OR(probes) AS or_result
    FROM
        auth.exec_class_data
    -- WHERE class_name like '%src/app/settings%' -- filter by class
    GROUP BY
        class_name
    -- ORDER BY set_bits_percentage ASC -- order by coverage amount
    * */

    coverage.groupBy { it.className }
    println("${agent.size} ${ast.size} ${coverage.size}")
}
