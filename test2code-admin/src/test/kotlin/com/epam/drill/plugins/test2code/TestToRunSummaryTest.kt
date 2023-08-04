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
package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.storage.*
import kotlinx.coroutines.*
import kotlin.test.*

class TestToRunSummaryTest : PluginTest() {
    @Test
    fun `for the first and second build testsToRunSummaries will be empty`() = runBlocking {
        val version1 = "0.1.0"
        val plugin = initPlugin(version1)
        assertTrue(
            plugin.storeClient.loadTestsToRunSummary(
                agentKey = AgentKey(agentId, version1)
            ).isEmpty()
        )

        val version2 = "0.2.0"
        val plugin2 = initPlugin(version2)
        assertTrue(
            plugin2.storeClient.loadTestsToRunSummary(
                agentKey = AgentKey(agentId, version2),
                parentVersion = version1
            ).isEmpty()
        )
    }

    @Test
    fun `for the third build testsToRunSummaries will have one element`() = runBlocking {
        val version1 = "0.1.0"
        initPlugin(version1)
        val version2 = "0.2.0"
        initPlugin(version2)

        val currentVersion = "0.3.0"
        val plugin3 = initPlugin(currentVersion)
        val testsToRunSummary = plugin3.storeClient.loadTestsToRunSummary(
            agentKey = AgentKey(agentId, currentVersion),
            parentVersion = version1
        )
        assertEquals(1, testsToRunSummary.size)
        assertEquals(version2, testsToRunSummary.first().agentKey.buildVersion)
        assertEquals(version1, testsToRunSummary.first().parentVersion)
    }

    @Test
    fun `testsToRunSummaries should be sorted by timestamp`() = runBlocking {
        val version1 = "0.1.0"
        initPlugin(version1)
        initPlugin("0.2.0")
        initPlugin("0.3.0")
        val currentVersion = "0.4.0"
        val plugin4 = initPlugin(currentVersion)

        val testsToRunSummaries = plugin4.storeClient.loadTestsToRunSummary(
            agentKey = AgentKey(agentId, currentVersion),
            parentVersion = version1
        )
        assertEquals(2, testsToRunSummaries.size)
        assertTrue(testsToRunSummaries.first().lastModifiedAt < testsToRunSummaries.last().lastModifiedAt)
    }
}
