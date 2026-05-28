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
package com.epam.drill.admin.etl.pipeline

import com.epam.drill.admin.etl.UntypedRow
import com.epam.drill.admin.etl.impl.UntypedAggregationTransformer
import com.epam.drill.admin.etl.impl.UntypedFilterTransformer
import com.epam.drill.admin.etl.impl.UntypedSqlDataExtractor
import com.epam.drill.admin.etl.impl.UntypedSqlDataLoader
import com.epam.drill.admin.etl.config.EtlConfig
import com.epam.drill.admin.etl.impl.pipeline
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.metrics.config.fromResource
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import org.postgresql.util.PGobject

val EtlConfig.coverageExtractor
    get() = UntypedSqlDataExtractor(
        name = "coverage",
        sqlQuery = fromResource("/etl/db/metrics/coverage_extractor.sql"),
        database = RawDataWriterDatabaseConfig.database,
        fetchSize = fetchSize,
        extractionLimit = extractionLimit,
        loggingFrequency = loggingFrequency,
        metrics = metrics,
        lastExtractedAtColumnName = "created_at",
    )

val EtlConfig.testLaunchCoverageExtractor
    get() = UntypedSqlDataExtractor(
        name = "test_launch_coverage",
        sqlQuery = fromResource("/etl/db/metrics/test_launch_coverage_extractor.sql"),
        database = RawDataWriterDatabaseConfig.database,
        fetchSize = fetchSize,
        extractionLimit = extractionLimit,
        loggingFrequency = loggingFrequency,
        lastExtractedAtColumnName = "test_completed_at",
        metrics = metrics,
    )

val EtlConfig.buildMethodTestDefinitionCoverageLoader
    get() = UntypedSqlDataLoader(
        name = "build_method_test_definition_coverage",
        sqlUpsert = fromResource("/etl/db/metrics/build_method_test_definition_coverage_loader.sql"),
        sqlDelete = fromResource("/etl/db/metrics/build_method_test_definition_coverage_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize,
        loggingFrequency = loggingFrequency,
        metrics = metrics,
    )

val EtlConfig.buildMethodTestSessionCoverageLoader
    get() = UntypedSqlDataLoader(
        name = "build_method_test_session_coverage",
        sqlUpsert = fromResource("/etl/db/metrics/build_method_test_session_coverage_loader.sql"),
        sqlDelete = fromResource("/etl/db/metrics/build_method_test_session_coverage_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize,
        loggingFrequency = loggingFrequency,
        metrics = metrics,
    )

val EtlConfig.buildMethodCoverageLoader
    get() = UntypedSqlDataLoader(
        name = "build_method_coverage",
        sqlUpsert = fromResource("/etl/db/metrics/build_method_coverage_loader.sql"),
        sqlDelete = fromResource("/etl/db/metrics/build_method_coverage_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize,
        loggingFrequency = loggingFrequency,
        metrics = metrics,
    )

val EtlConfig.methodDailyCoverageLoader
    get() = UntypedSqlDataLoader(
        name = "method_daily_coverage",
        sqlUpsert = fromResource("/etl/db/metrics/method_daily_coverage_loader.sql"),
        sqlDelete = fromResource("/etl/db/metrics/method_daily_coverage_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize,
        loggingFrequency = loggingFrequency,
        metrics = metrics,
    )

val EtlConfig.test2CodeMappingLoader
    get() = UntypedSqlDataLoader(
        name = "test_to_code_mapping",
        sqlUpsert = fromResource("/etl/db/metrics/test_to_code_mapping_loader.sql"),
        sqlDelete = fromResource("/etl/db/metrics/test_to_code_mapping_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize,
        loggingFrequency = loggingFrequency,
        metrics = metrics,
    )

val EtlConfig.hasTestSessionFilter
    get() = UntypedFilterTransformer(
        name = "test_session_filter",
        metrics = metrics,
        predicate = { it["test_session_id"] != null },
    )

val EtlConfig.hasTestSessionAndDefinitionFilter
    get() = UntypedFilterTransformer(
        name = "test_definition_filter",
        metrics = metrics,
        predicate = { it["test_session_id"] != null && it["test_definition_id"] != null },
    )

val EtlConfig.buildMethodCoverageAggregator
    get() = UntypedAggregationTransformer(
        name = "build_method_coverage_aggregator",
        bufferSize = transformationBufferSize,
        loggingFrequency = loggingFrequency,
        metrics = metrics,
        groupKeys = listOf(
            "group_id",
            "app_id",
            "build_id",
            "method_id",
            "app_env_id",
            "test_result",
            "test_tag",
            "test_task_id"
        ),
        aggregate = { current, next ->
            val map = HashMap<String, Any?>(current)
            map["probes"] = mergeProbes(current["probes"], next["probes"])
            map["created_at_day"] = next["created_at_day"]
            UntypedRow(next.timestamp, map)
        },
    )

val EtlConfig.methodDailyCoverageAggregator
    get() = UntypedAggregationTransformer(
        name = "method_daily_coverage_aggregator",
        bufferSize = transformationBufferSize,
        loggingFrequency = loggingFrequency,
        metrics = metrics,
        groupKeys = listOf(
            "group_id",
            "app_id",
            "method_id",
            "created_at_day",
            "branch",
            "app_env_id",
            "test_result",
            "test_tag",
            "test_task_id"
        ),
        aggregate = { current, next ->
            val map = HashMap<String, Any?>(current)
            map["probes"] = mergeProbes(current["probes"], next["probes"])
            UntypedRow(next.timestamp, map)
        },
    )

val EtlConfig.buildMethodTestSessionCoveragePipeline
    get() = pipeline("build_method_test_session_coverage")
        .extractWith(coverageExtractor)
        .transformWith(hasTestSessionFilter)
        .loadWith(buildMethodTestSessionCoverageLoader)

val EtlConfig.buildMethodCoveragePipeline
    get() = pipeline("build_method_coverage")
        .extractWith(coverageExtractor)
        .transformWith(buildMethodCoverageAggregator)
        .loadWith(buildMethodCoverageLoader)

val EtlConfig.methodDailyCoveragePipeline
    get() = pipeline("method_daily_coverage")
        .extractWith(coverageExtractor)
        .transformWith(methodDailyCoverageAggregator)
        .loadWith(methodDailyCoverageLoader)

val EtlConfig.testSessionBuildsFromCoveragePipeline
    get() = pipeline("test_session_builds_from_coverage")
        .extractWith(coverageExtractor)
        .transformWith(hasTestSessionFilter)
        .loadWith(testSessionBuildsLoader)

val EtlConfig.buildMethodTestDefinitionCoveragePipeline
    get() = pipeline("build_method_test_definition_coverage")
        .extractWith(testLaunchCoverageExtractor)
        .transformWith(hasTestSessionAndDefinitionFilter)
        .loadWith(buildMethodTestDefinitionCoverageLoader)

val EtlConfig.buildMethodTestSessionCoverageFromTestLaunchesPipeline
    get() = pipeline("build_method_test_session_coverage_from_test_launches")
        .extractWith(testLaunchCoverageExtractor)
        .transformWith(hasTestSessionFilter)
        .loadWith(buildMethodTestSessionCoverageLoader)

val EtlConfig.buildMethodCoverageFromTestLaunchesPipeline
    get() = pipeline("build_method_coverage_from_test_launches")
        .extractWith(testLaunchCoverageExtractor)
        .transformWith(buildMethodCoverageAggregator)
        .loadWith(buildMethodCoverageLoader)

val EtlConfig.methodDailyCoverageFromTestLaunchesPipeline
    get() = pipeline("method_daily_coverage_from_test_launches")
        .extractWith(testLaunchCoverageExtractor)
        .transformWith(methodDailyCoverageAggregator)
        .loadWith(methodDailyCoverageLoader)

val EtlConfig.test2CodeMappingPipeline
    get() = pipeline("test_to_code_mapping")
        .extractWith(testLaunchCoverageExtractor)
        .filter { it["test_definition_id"] != null && it["test_result"] == "PASSED" }
        .aggregateBy(
            "group_id",
            "app_id",
            "signature",
            "test_definition_id",
            "branch",
            "app_env_id",
            "test_task_id"
        ) { current, next ->
            val map = HashMap<String, Any?>(current)
            map["updated_at_day"] = next["created_at_day"]
            UntypedRow(next.timestamp, map)
        }
        .loadWith(test2CodeMappingLoader)

val EtlConfig.testSessionBuildsFromTestLaunchesPipeline
    get() = pipeline("test_session_builds_from_test_launches")
        .extractWith(testLaunchCoverageExtractor)
        .transformWith(hasTestSessionFilter)
        .loadWith(testSessionBuildsLoader)

internal fun mergeProbes(current: Any?, next: Any?): PGobject {
    if (current == null || next == null) {
        throw IllegalArgumentException("Cannot merge null probes: current=$current, next=$next")
    }
    if (current !is PGobject || next !is PGobject) {
        throw IllegalArgumentException("Probes must be of type PGobject: current=${current.javaClass.name}, next=${next.javaClass.name}")
    }
    val nextProbes = next.value ?: ""
    val currentProbes = current.value ?: ""
    if (currentProbes.length != nextProbes.length)
        throw IllegalArgumentException("Cannot merge probes of different lengths: current=${currentProbes.length}, next=${nextProbes.length}")
    val mergedProbes = buildString(currentProbes.length) {
        for (i in 0 until currentProbes.length) {
            append(if (currentProbes[i] == '1' || nextProbes[i] == '1') '1' else '0')
        }
    }
    return PGobject().apply {
        type = "varbit"
        value = mergedProbes
    }
}