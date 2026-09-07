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
import com.epam.drill.admin.etl.impl.UntypedSqlDataExtractor
import com.epam.drill.admin.etl.impl.pipeline
import com.epam.drill.admin.metrics.config.fromResource
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig

val EtlConfig.coverageExtractor
    get() = UntypedSqlDataExtractor(
        name = "coverage",
        sqlQuery = fromResource("/metrics/db/etl/coverage_extractor.sql"),
        database = RawDataWriterDatabaseConfig.database,
        fetchSize = fetchSize,
        extractionLimit = extractionLimit,
        loggingFrequency = loggingFrequency,
        metrics = metrics,
        lastExtractedAtColumnName = "created_at",
    )

val EtlConfig.historicalBuildMethodTestSessionCoveragePipeline
    get() = pipeline("build_method_test_session_coverage")
        .extractWith(coverageExtractor)
        .transformWith(hasTestSessionFilter)
        .transformWith(buildMethodTestSessionCoverageAggregator)
        .loadWith(buildMethodTestSessionCoverageLoader)

val EtlConfig.historicalBuildMethodCoveragePipeline
    get() = pipeline("build_method_coverage")
        .extractWith(coverageExtractor)
        .transformWith(buildMethodCoverageAggregator)
        .loadWith(buildMethodCoverageLoader)

val EtlConfig.historicalMethodCoveragePipeline
    get() = pipeline("method_daily_coverage")
        .extractWith(coverageExtractor)
        .transformWith(methodCoverageAggregator)
        .loadWith(methodCoverageLoader)

val EtlConfig.historicalTestSessionBuildsPipeline
    get() = pipeline("test_session_builds_from_coverage")
        .extractWith(coverageExtractor)
        .transformWith(hasTestSessionFilter)
        .transformWith(testSessionBuildsAggregator)
        .loadWith(testSessionBuildsLoader)

val EtlConfig.historicalBuildMethodTestDefinitionCoveragePipeline
    get() = pipeline("build_method_test_definition_coverage")
        .extractWith(coverageExtractor)
        .transformWith(hasTestSessionAndDefinitionFilter)
        .loadWith(buildMethodTestDefinitionCoverageLoader)


val EtlConfig.historicalTest2CodeMappingPipeline
    get() = pipeline("test_to_code_mapping")
        .extractWith(coverageExtractor)
        .transformWith(testPassedFilter)
        .transformWith(test2CodeCoverageAggregator)
        .loadWith(test2CodeMappingLoader)