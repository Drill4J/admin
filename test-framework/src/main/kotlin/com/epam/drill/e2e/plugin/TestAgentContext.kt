/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.e2e.plugin

import com.epam.drill.plugin.api.processing.*
import kotlinx.atomicfu.*

class TestAgentContext : AgentContext {

    private val _data = atomic(emptyMap<String, String>())

    private val sessionIdHeader = "drill-session-id"
    private val testNameHeader = "drill-test-name"
    private val testHashHeader = "drill-test-id"

    override fun invoke(): String? = _data.value[sessionIdHeader]

    override fun get(key: String): String? = _data.value[key]

    fun runWithSession(
        sessionId: String,
        testName: String,
        testHash: String,
        agentPart: AgentPart<*>?,
        block: () -> Unit,
    ) {
        _data.value = mapOf(
            sessionIdHeader to sessionId,
            testNameHeader to testName,
            testHashHeader to testHash,
        )
        runCatching {
            agentPart?.javaClass?.getDeclaredMethod("processServerRequest")?.invoke(agentPart)
        }
        try {
            block()
        } finally {
            _data.value = emptyMap()
        }

    }
}
