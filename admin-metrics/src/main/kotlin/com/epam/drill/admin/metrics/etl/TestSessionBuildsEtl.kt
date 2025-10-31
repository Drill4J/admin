package com.epam.drill.admin.metrics.etl

import com.epam.drill.admin.etl.impl.EtlPipelineImpl
import com.epam.drill.admin.etl.impl.SqlDataExtractor
import com.epam.drill.admin.etl.impl.SqlDataLoader
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.metrics.config.fromResource

val testSessionBuildsExtractor = SqlDataExtractor(
    name = "test_session_builds",
    sqlQuery = fromResource("/metrics/db/etl/test_session_builds_extractor.sql"),
    database = MetricsDatabaseConfig.database
)

val testSessionBuildsLoader = SqlDataLoader(
    name = "test_session_builds",
    sqlUpsert = fromResource("/metrics/db/etl/test_session_builds_loader.sql"),
    lastExtractedAtColumnName = "created_at",
    database = MetricsDatabaseConfig.database
)

val testSessionBuildsPipeline = EtlPipelineImpl(
    name = "test_session_builds",
    extractor = testSessionBuildsExtractor,
    loaders = listOf(testSessionBuildsLoader)
)

