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

import com.epam.drill.admin.etl.UntypedRow
import com.epam.drill.admin.etl.impl.EtlPipelineImpl
import com.epam.drill.admin.etl.impl.UntypedGroupAggregateTransformer
import com.epam.drill.admin.etl.impl.UntypedSqlDataExtractor
import com.epam.drill.admin.etl.impl.UntypedSqlDataLoader
import com.epam.drill.admin.etl.untypedNopTransformer
import com.epam.drill.admin.metrics.config.EtlConfig
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.metrics.config.fromResource
import org.postgresql.util.PGobject


val EtlConfig.coverageExtractor
    get() = UntypedSqlDataExtractor(
        name = "coverage",
        sqlQuery = fromResource("/metrics/db/etl/coverage_extractor.sql"),
        database = MetricsDatabaseConfig.database,
        fetchSize = fetchSize,
        extractionLimit = extractionLimit,
        lastExtractedAtColumnName = "created_at",
    )

val EtlConfig.buildMethodTestDefinitionCoverageLoader
    get() = UntypedSqlDataLoader(
        name = "build_method_test_definition_coverage",
        sqlUpsert = fromResource("/metrics/db/etl/build_method_test_definition_coverage_loader.sql"),
        sqlDelete = fromResource("/metrics/db/etl/build_method_test_definition_coverage_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize,
        processable = { it["test_session_id"] != null && it["test_definition_id"] != null }
    )

val EtlConfig.buildMethodTestSessionCoverageLoader
    get() = UntypedSqlDataLoader(
        name = "build_method_test_session_coverage",
        sqlUpsert = fromResource("/metrics/db/etl/build_method_test_session_coverage_loader.sql"),
        sqlDelete = fromResource("/metrics/db/etl/build_method_test_session_coverage_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize,
        processable = { it["test_session_id"] != null }
    )


val EtlConfig.buildMethodCoverageTransformer
    get() = UntypedGroupAggregateTransformer(
        name = "build_method_coverage",
        bufferSize = transformationBufferSize,
        groupKeys = listOf(
            "group_id",
            "app_id",
            "build_id",
            "method_id",
            "app_env_id",
            "test_result",
            "test_tag",
            "test_task_id",
        )
    ) { current, next ->
        val map = HashMap<String, Any?>(current)
        map["probes"] = mergeProbes(current["probes"], next["probes"])
        map["created_at_day"] = next["created_at_day"]
        UntypedRow(next.timestamp, map)
    }


val EtlConfig.buildMethodCoverageLoader
    get() = UntypedSqlDataLoader(
        name = "build_method_coverage",
        sqlUpsert = fromResource("/metrics/db/etl/build_method_coverage_loader.sql"),
        sqlDelete = fromResource("/metrics/db/etl/build_method_coverage_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize
    )

val EtlConfig.methodDailyCoverageTransformer
    get() = UntypedGroupAggregateTransformer(
        name = "method_daily_coverage",
        bufferSize = transformationBufferSize,
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
        )
    ) { current, next ->
        val map = HashMap<String, Any?>(current)
        map["probes"] = mergeProbes(current["probes"], next["probes"])
        UntypedRow(next.timestamp, map)
    }

val EtlConfig.methodDailyCoverageLoader
    get() = UntypedSqlDataLoader(
        name = "method_daily_coverage",
        sqlUpsert = fromResource("/metrics/db/etl/method_daily_coverage_loader.sql"),
        sqlDelete = fromResource("/metrics/db/etl/method_daily_coverage_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize
    )

val EtlConfig.test2CodeMappingLoader
    get() = UntypedSqlDataLoader(
        name = "test_to_code_mapping",
        sqlUpsert = fromResource("/metrics/db/etl/test_to_code_mapping_loader.sql"),
        sqlDelete = fromResource("/metrics/db/etl/test_to_code_mapping_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize,
        processable = { it["test_definition_id"] != null && it["test_result"] == "PASSED" }
    )

val EtlConfig.coveragePipeline
    get() = EtlPipelineImpl(
        name = "coverage",
        extractor = coverageExtractor,
        loaders = listOf(
            untypedNopTransformer to buildMethodTestDefinitionCoverageLoader,
            untypedNopTransformer to buildMethodTestSessionCoverageLoader,
            buildMethodCoverageTransformer to buildMethodCoverageLoader,
            methodDailyCoverageTransformer to methodDailyCoverageLoader,
            untypedNopTransformer to test2CodeMappingLoader,
            untypedNopTransformer to testSessionBuildsLoader
        ),
        bufferSize = bufferSize
    )

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
            val currentBit = currentProbes[i]
            val nextBit = nextProbes[i]
            append(if (currentBit == '1' || nextBit == '1') '1' else '0')
        }
    }
    return PGobject().apply {
        type = "varbit"
        value = mergedProbes
    }
}