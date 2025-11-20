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

val EtlConfig.testSessionsExtractor
    get() = UntypedSqlDataExtractor(
        name = "test_sessions",
        sqlQuery = fromResource("/metrics/db/etl/test_sessions_extractor.sql"),
        database = MetricsDatabaseConfig.database,
        fetchSize = fetchSize
    )

val EtlConfig.testSessionsLoader
    get() = UntypedSqlDataLoader(
        name = "test_sessions",
        sqlUpsert = fromResource("/metrics/db/etl/test_sessions_loader.sql"),
        sqlDelete = fromResource("/metrics/db/etl/test_sessions_delete.sql"),
        lastExtractedAtColumnName = "created_at",
        database = MetricsDatabaseConfig.database,
        batchSize = batchSize
    )

val EtlConfig.testSessionsPipeline
    get() = EtlPipelineImpl(
        name = "test_sessions",
        extractor = testSessionsExtractor,
        loaders = listOf(testSessionsLoader),
        bufferSize = bufferSize
    )
