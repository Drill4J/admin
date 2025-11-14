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
    fun `given full tests launched on baseline build, impacted tests service should return only impacted tests compared to baseline`() =
        havingData {
            build1 has listOf(method1, method2)
            test1 covers method1 on build1
            test2 covers method2 on build1
            build2 hasModified method2 comparedTo build1
        }.expectThat {
            test2 isImpactedOn build2 comparedTo build1
            test1 isNotImpactedOn build2 comparedTo build1
            method2 isImpactedOn build2 comparedTo build1
            method1 isNotImpactedOn build2 comparedTo build1
        }

    @Test
    fun `given partial tests launched on baseline build, impacted tests service should return missing impacted tests on other builds`() =
        havingData {
            build1 has listOf(method1, method2)
            test1 covers method1 on build1
            test2 covers method2 on build1
            build2 hasModified method2 comparedTo build1
            test2 covers method2 on build2
            build3 hasModified method1 comparedTo build2
        }.expectThat {
            test1 isImpactedOn build3 comparedTo build2
            test2 isNotImpactedOn build3 comparedTo build2
            method1 isImpactedOn build3 comparedTo build2
            method2 isNotImpactedOn build3 comparedTo build2
        }

    @Test
    fun `given deleted method compared to baseline, impacted tests service should return tests witch cover deleted method`() =
        havingData {
            build1 has listOf(method1, method2)
            test1 covers method1 on build1
            build2 hasDeleted method1 comparedTo build1
        }.expectThat {
            test1 isImpactedOn build2 comparedTo build1
            method1 isImpactedOn build2 comparedTo build1
//            build2 hasDeleted method1 comparedTo build1 TODO includeDeleted is not implemented yet in /metrics/changes endpoint
        }

    @Test
    fun `given no changes compared to baseline, impacted tests service should return no impacted tests`() =
        havingData {
            build1 has listOf(method1)
            build2 hasNew method2 comparedTo build1
            test1 covers method2 on build2
            build3 hasDeleted method2 comparedTo build2
        }.expectThat {
            test1 isNotImpactedOn build3 comparedTo build1
            method2 isNotImpactedOn build3 comparedTo build1
            build3 hasTheSameMethodsComparedTo build1
        }

    @Test
    fun `given new method compared to baseline, impacted tests service should return no impacted tests`() =
        havingData {
            build1 has listOf(method1)
            build2 hasNew method2 comparedTo build1
            test1 covers method2 on build2
            build3 hasModified method2 comparedTo build2
        }.expectThat {
//            TODO new methods are not ignored yet in impacted tests analysis
//            test1 isNotImpactedOn build3 comparedTo build1
//            method2 isNotImpactedOn build3 comparedTo build1
            test1 isImpactedOn build3 comparedTo build1
            method2 isImpactedOn build3 comparedTo build1
            build3 hasNew method2 comparedTo build1
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

