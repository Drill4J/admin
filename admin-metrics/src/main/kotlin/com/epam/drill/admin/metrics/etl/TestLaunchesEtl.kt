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
import com.epam.drill.admin.etl.impl.SqlDataExtractor
import com.epam.drill.admin.etl.impl.SqlDataLoader
import com.epam.drill.admin.etl.impl.UntypedSqlDataExtractor
import com.epam.drill.admin.etl.impl.UntypedSqlDataLoader
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.metrics.config.fromResource

val testLaunchesExtractor = UntypedSqlDataExtractor(
    name = "test_launches",
    sqlQuery = fromResource("/metrics/db/etl/test_launches_extractor.sql"),
    database = MetricsDatabaseConfig.database
)

val testLaunchesLoader = UntypedSqlDataLoader(
    name = "test_launches",
    sql = fromResource("/metrics/db/etl/test_launches_loader.sql"),
    lastExtractedAtColumnName = "created_at",
    database = MetricsDatabaseConfig.database
)

val testLaunchesPipeline = EtlPipelineImpl(
    name = "test_launches",
    extractor = testLaunchesExtractor,
    loaders = listOf(testLaunchesLoader)
)

