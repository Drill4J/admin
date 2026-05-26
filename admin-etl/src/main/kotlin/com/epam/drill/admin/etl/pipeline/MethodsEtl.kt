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
import com.epam.drill.admin.etl.impl.UntypedSqlDataExtractor
import com.epam.drill.admin.etl.impl.UntypedSqlDataLoader
import com.epam.drill.admin.etl.config.EtlConfig
import com.epam.drill.admin.etl.impl.pipeline
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.metrics.config.fromResource
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig

val EtlConfig.buildMethodsExtractor
    get() = UntypedSqlDataExtractor(
        name = "build_methods",
        sqlQuery = fromResource("/etl/db/metrics/build_methods_extractor.sql"),
        database = RawDataWriterDatabaseConfig.database,
        fetchSize = fetchSize,
        extractionLimit = extractionLimit,
        lastExtractedAtColumnName = "created_at",
        metrics = metrics,
    )

val EtlConfig.buildMethodsLoader
    get() = UntypedSqlDataLoader(
        name = "build_methods",
        sqlUpsert = fromResource("/etl/db/metrics/build_methods_loader.sql"),
        sqlDelete = fromResource("/etl/db/metrics/build_methods_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize,
        metrics = metrics,
    )

val EtlConfig.methodsLoader
    get() = UntypedSqlDataLoader(
        name = "methods",
        sqlUpsert = fromResource("/etl/db/metrics/methods_loader.sql"),
        sqlDelete = fromResource("/etl/db/metrics/methods_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize,
        metrics = metrics,
    )

val EtlConfig.buildMethodsPipeline
    get() = pipeline("build_methods")
        .extractWith(buildMethodsExtractor)
        .fanOut {
            loadWith(buildMethodsLoader)
            aggregateBy("group_id", "app_id", "method_id") { _, next ->
                UntypedRow(next.timestamp, next)
            }.loadWith(methodsLoader)
        }
