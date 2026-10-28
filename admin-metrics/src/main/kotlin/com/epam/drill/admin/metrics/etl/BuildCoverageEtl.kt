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
package com.epam.drill.admin.metrics.etl

import com.epam.drill.admin.etl.config.EtlConfig
import com.epam.drill.admin.etl.impl.UntypedSqlDataLoader
import com.epam.drill.admin.etl.impl.pipeline
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.metrics.config.fromResource
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig

val EtlConfig.buildCoverageLoader
    get() = UntypedSqlDataLoader(
        name = "build_coverage",
        sqlUpsert = fromResource("/metrics/db/etl/build_coverage_loader.sql"),
        sqlDelete = fromResource("/metrics/db/etl/build_coverage_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize,
        metrics = metrics,
    )

val EtlConfig.buildCoverageAggregator
    get() = coverageAggregator(
        name = "build_coverage_aggregator", groupKeys = listOf(
            "group_id",
            "app_id",
            "build_id",
            "app_env_id",
            "test_result",
            "test_session_id",
            "test_project_id",
            "created_at_day"
        ), probeKeys = listOf(
            "code_probes",
            "method_probes"
        )
    )

val EtlConfig.buildCoverageEnricher
    get() = BuildCoverageTransformer(
        name = "build_coverage_enricher",
        database = RawDataWriterDatabaseConfig.database,
        lookupCacheSize = buildProbeLayoutCacheSize,
        metrics = metrics,
    )

val EtlConfig.incrementalBuildCoveragePipeline
    get() = pipeline("incremental_build_coverage")
        .extractWith(globalCoverageExtractor)
        .transformWith(buildCoverageEnricher)
        .transformWith(buildCoverageAggregator)
        .loadWith(buildCoverageLoader)

val EtlConfig.incrementalBuildCoverageFromTestLaunchesPipeline
    get() = pipeline("incremental_build_coverage_from_test_launches")
        .extractWith(testLaunchCoverageExtractor)
        .transformWith(buildCoverageEnricher)
        .transformWith(buildCoverageAggregator)
        .loadWith(buildCoverageLoader)

val EtlConfig.historicalBuildCoveragePipeline
    get() = pipeline("historical_build_coverage")
        .extractWith(coverageExtractor)
        .transformWith(buildCoverageEnricher)
        .transformWith(buildCoverageAggregator)
        .loadWith(buildCoverageLoader)
