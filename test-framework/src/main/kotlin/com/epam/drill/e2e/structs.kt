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
package com.epam.drill.e2e

import com.epam.drill.builds.*

data class AgentAsyncStruct(
    val ag: AgentWrap,
    val build: Build,
    val callback: suspend PluginTestContext.(Any, Any) -> Unit,
    val thenCallbacks: MutableList<ThenAgentAsyncStruct> = mutableListOf()
)

data class ThenAgentAsyncStruct(
    val ag: AgentWrap,
    val build: Build,
    val needSync: Boolean,
    val callback: suspend PluginTestContext.(Any, Any) -> Unit
)


class AgentDatum(val classMap: Map<String, ByteArray>)
