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

import com.epam.drill.admin.etl.impl.UntypedSqlDataExtractor
import com.epam.drill.admin.etl.impl.UntypedSqlDataLoader
import com.epam.drill.admin.etl.config.EtlConfig
import com.epam.drill.admin.etl.impl.pipeline
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.metrics.config.fromResource
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig

val EtlConfig.testSessionsExtractor
    get() = UntypedSqlDataExtractor(
        name = "test_sessions",
        sqlQuery = fromResource("/etl/db/metrics/test_sessions_extractor.sql"),
        database = RawDataWriterDatabaseConfig.database,
        fetchSize = fetchSize,
        extractionLimit = extractionLimit,
        loggingFrequency = loggingFrequency,
        lastExtractedAtColumnName = "created_at",
        metrics = metrics,
    )

val EtlConfig.testSessionsLoader
    get() = UntypedSqlDataLoader(
        name = "test_sessions",
        sqlUpsert = fromResource("/etl/db/metrics/test_sessions_loader.sql"),
        sqlDelete = fromResource("/etl/db/metrics/test_sessions_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize,
        loggingFrequency = loggingFrequency,
        metrics = metrics,
    )

val EtlConfig.testSessionsPipeline
    get() = pipeline("test_sessions")
        .extractWith(testSessionsExtractor)
        .loadWith(testSessionsLoader)
