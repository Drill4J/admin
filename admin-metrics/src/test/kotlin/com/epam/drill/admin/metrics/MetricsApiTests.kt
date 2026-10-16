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
package com.epam.drill.admin.metrics

import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.metrics.config.executeUpdate
import com.epam.drill.admin.test.MetricsDatabaseTests
import com.epam.drill.admin.test.withTransaction
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.table.BuildMethodTable
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.epam.drill.admin.writer.rawdata.table.InstanceTable
import com.epam.drill.admin.writer.rawdata.table.MethodCoverageTable
import com.epam.drill.admin.writer.rawdata.table.MethodIgnoreRulesTable
import com.epam.drill.admin.writer.rawdata.table.MethodTable
import com.epam.drill.admin.writer.rawdata.table.TestDefinitionTable
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import com.epam.drill.admin.writer.rawdata.table.TestSessionBuildTable
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach

abstract class MetricsApiTests : MetricsDatabaseTests(initialization = { default, metrics ->
    RawDataWriterDatabaseConfig.init(default)
    MetricsDatabaseConfig.init(metrics)
}) {
    @AfterEach
    fun cleanupDatabase() {
        withTransaction(RawDataWriterDatabaseConfig.database) {
            MethodCoverageTable.deleteAll()
            TestLaunchTable.deleteAll()
            TestDefinitionTable.deleteAll()
            TestSessionBuildTable.deleteAll()
            TestSessionTable.deleteAll()
            BuildMethodTable.deleteAll()
            BuildTable.deleteAll()
            InstanceTable.deleteAll()
            MethodTable.deleteAll()
            MethodIgnoreRulesTable.deleteAll()
        }
        withTransaction(MetricsDatabaseConfig.database) {
            executeUpdate("DELETE FROM metrics.build_coverage")
            executeUpdate("DELETE FROM metrics.build_method_coverage")
            executeUpdate("DELETE FROM metrics.build_method_test_session_coverage")
            executeUpdate("DELETE FROM metrics.build_method_test_definition_coverage")
            executeUpdate("DELETE FROM metrics.test_to_code_mapping")
            executeUpdate("DELETE FROM metrics.method_daily_coverage")
            executeUpdate("DELETE FROM metrics.test_launches")
            executeUpdate("DELETE FROM metrics.test_definitions")
            executeUpdate("DELETE FROM metrics.test_sessions")
            executeUpdate("DELETE FROM metrics.build_methods")
            executeUpdate("DELETE FROM metrics.builds")
        }
    }
}