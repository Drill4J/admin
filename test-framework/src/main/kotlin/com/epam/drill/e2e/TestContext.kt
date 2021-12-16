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
import java.util.*
import java.util.concurrent.*

class TestContext<T : PluginStreams>(val agents: MutableMap<String, AgentAsyncStruct> = ConcurrentHashMap()) {

    inline fun <reified B : Build> connectAgent(
        group: String = "",
        ags: AgentWrap = AgentWrap(
            id = UUID.randomUUID().toString().replace("-", ""),
            instanceId = "1",
            buildVersion = B::class.objectInstance!!.version,
            needSync = true,
            groupId = group
        ),
        noinline bl: suspend PluginTestContext.(T, B) -> Unit
    ): TestContext<T> {

        val kClass = B::class
        val build = kClass.objectInstance!!
        @Suppress("UNCHECKED_CAST")
        agents[ags.id] = AgentAsyncStruct(
            ags,
            build,
            bl as suspend PluginTestContext.(Any, Any) -> Unit
        )
        return this
    }

    inline fun <reified B : Build> reconnect(
        group: String = "",
        ags: AgentWrap = AgentWrap(
            id = agents.keys.first(),
            instanceId = "1",
            buildVersion = B::class.objectInstance!!.version,
            groupId = group
        ),
        noinline bl: suspend PluginTestContext.(T, B) -> Unit
    ): TestContext<T> {
        val needSync = agents[ags.id]?.build?.version != ags.buildVersion
        @Suppress("UNCHECKED_CAST")
        agents[ags.id]?.thenCallbacks?.add(
            ThenAgentAsyncStruct(
                ags,
                B::class.objectInstance!!,
                needSync,
                bl as suspend PluginTestContext.(Any, Any) -> Unit
            )
        )
        return this
    }

}
