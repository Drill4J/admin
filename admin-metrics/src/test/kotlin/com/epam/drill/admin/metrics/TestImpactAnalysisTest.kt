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
import com.epam.drill.admin.test.DatabaseTests
import com.epam.drill.admin.test.withTransaction
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.epam.drill.admin.writer.rawdata.table.CoverageTable
import com.epam.drill.admin.writer.rawdata.table.InstanceTable
import com.epam.drill.admin.writer.rawdata.table.MethodTable
import com.epam.drill.admin.writer.rawdata.table.TestDefinitionTable
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import org.jetbrains.exposed.sql.deleteAll
import kotlin.test.AfterTest
import kotlin.test.Test

class TestImpactAnalysisTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {

    @Test
    fun `given modified methods compared to baseline, impacted tests service should return tests which cover modified methods`() =
        havingData {
            build1 has listOf(method1)
            test1 covers method1 on build1
            build2 hasModified method1 comparedTo build1
        }.expectThat {
            test1 isImpactedOn build2 comparedTo build1
            method1 isImpactedOn build2 comparedTo build1
            //because
            method1 isModifiedOn build2 comparedTo build1
        }

    @Test
    fun `given equal methods compared to baseline, impacted tests service should not return tests which cover equal methods`() =
        havingData {
            build1 has listOf(method1)
            test1 covers method1 on build1
            build2 hasModified method1 comparedTo build1
            test1 covers method1 on build2
            build3 hasTheSameMethodsComparedTo build2
        }.expectThat {
            test1 isNotImpactedOn build3 comparedTo build2
            method1 isNotImpactedOn build3 comparedTo build2
            //because
            //method1 isEqualOn build3 comparedTo build2 TODO includeEqual is not implemented yet in /metrics/changes endpoint
        }

    @Test
    fun `given deleted methods compared to baseline, impacted tests service should return tests witch cover deleted methods`() =
        havingData {
            build1 has listOf(method1, method2)
            test1 covers method1 on build1
            build2 hasDeleted method1 comparedTo build1
        }.expectThat {
            test1 isImpactedOn build2 comparedTo build1
            method1 isImpactedOn build2 comparedTo build1
//            method1 isDeletedOn build2 comparedTo build1 TODO includeDeleted is not implemented yet in /metrics/changes endpoint
        }

    @Test
    fun `given new methods compared to baseline, impacted tests service should return tests which cover new methods`() =
        havingData {
            build1 has listOf(method1)
            build2 hasNew method2 comparedTo build1
            test1 covers method2 on build2
        }.expectThat {
            test1 isImpactedOn build2 comparedTo build1
            method2 isImpactedOn build2 comparedTo build1
            //because
            method2 isNewOn build2 comparedTo build1
        }

    @Test
    fun `given tests covering non-existent methods on both target and baseline builds, impacted tests service should not return these tests`() =
        havingData {
            build1 has listOf(method1)
            build2 hasNew method2 comparedTo build1
            test1 covers method2 on build2
            build3 hasDeleted method2 comparedTo build2
        }.expectThat {
            test1 isNotImpactedOn build3 comparedTo build1
            method2 isNotImpactedOn build3 comparedTo build1
            //because method2 does not exist on both build3 and build1
        }

    @AfterTest
    fun clearAll() = withTransaction {
        CoverageTable.deleteAll()
        InstanceTable.deleteAll()
        MethodTable.deleteAll()
        BuildTable.deleteAll()
        TestLaunchTable.deleteAll()
        TestSessionTable.deleteAll()
        TestDefinitionTable.deleteAll()
    }
}

