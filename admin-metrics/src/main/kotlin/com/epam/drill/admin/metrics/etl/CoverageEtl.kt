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

import com.epam.drill.admin.etl.impl.EtlPipelineImpl
import com.epam.drill.admin.etl.impl.UntypedSqlDataExtractor
import com.epam.drill.admin.etl.impl.UntypedSqlDataLoader
import com.epam.drill.admin.metrics.config.EtlConfig
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.metrics.config.fromResource


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

val EtlConfig.buildMethodCoverageLoader
    get() = UntypedSqlDataLoader(
        name = "build_method_coverage",
        sqlUpsert = fromResource("/metrics/db/etl/build_method_coverage_loader.sql"),
        sqlDelete = fromResource("/metrics/db/etl/build_method_coverage_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize
    )

val EtlConfig.methodCoverageLoader
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
            buildMethodTestDefinitionCoverageLoader,
            buildMethodTestSessionCoverageLoader,
            buildMethodCoverageLoader,
            methodCoverageLoader,
            test2CodeMappingLoader,
            testSessionBuildsLoader
        ),
        bufferSize = bufferSize
    )