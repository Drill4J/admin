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
import com.epam.drill.admin.etl.impl.UntypedAggregationTransformer
import com.epam.drill.admin.etl.impl.UntypedSqlDataExtractor
import com.epam.drill.admin.etl.impl.UntypedSqlDataLoader
import com.epam.drill.admin.etl.untypedNopTransformer
import com.epam.drill.admin.metrics.config.EtlConfig
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.metrics.config.fromResource

val EtlConfig.buildMethodsExtractor
    get() = UntypedSqlDataExtractor(
        name = "build_methods",
        sqlQuery = fromResource("/metrics/db/etl/build_methods_extractor.sql"),
        database = MetricsDatabaseConfig.database,
        fetchSize = fetchSize,
        extractionLimit = extractionLimit,
        lastExtractedAtColumnName = "created_at",
    )

val EtlConfig.buildMethodsLoader
    get() = UntypedSqlDataLoader(
        name = "build_methods",
        sqlUpsert = fromResource("/metrics/db/etl/build_methods_loader.sql"),
        sqlDelete = fromResource("/metrics/db/etl/build_methods_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize
    )

val EtlConfig.methodLoaderTransformer
    get() = UntypedAggregationTransformer(
        name = "method_loader",
        bufferSize = transformationBufferSize,
        groupKeys = listOf(
            "group_id",
            "app_id",
            "method_id"
        )
    ) { current, next ->
        UntypedRow(next.timestamp, next)
    }

val EtlConfig.methodsLoader
    get() = UntypedSqlDataLoader(
        name = "methods",
        sqlUpsert = fromResource("/metrics/db/etl/methods_loader.sql"),
        sqlDelete = fromResource("/metrics/db/etl/methods_delete.sql"),
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize
    )

val EtlConfig.methodsPipeline
    get() = EtlPipelineImpl(
        name = "methods",
        extractor = buildMethodsExtractor,
        loaders = listOf(untypedNopTransformer to buildMethodsLoader, methodLoaderTransformer to methodsLoader),
        bufferSize = bufferSize
    )