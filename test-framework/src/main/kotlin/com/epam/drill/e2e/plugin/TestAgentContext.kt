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

    private val _data = atomic<Pair<String, String>?>(null)

    override fun invoke(): String? = _data.value?.first

    override fun get(key: String): String? = _data.value?.second

    fun runWithSession(sessionId: String, testName: String, block: () -> Unit) {
        _data.value = sessionId to testName
        try {
            block()
        } finally {
            _data.value = null
        }

    }
}
