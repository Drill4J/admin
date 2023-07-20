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
package com.epam.drill.plugins.test2code.group

import com.epam.drill.plugins.test2code.api.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

val sessionAggregator = SessionAggregator()

class SessionAggregator : (String, String, List<ActiveSessionDto>) -> List<ActiveSessionDto>? {
    private val _groups = atomic(
        persistentHashMapOf<String, PersistentMap<String, List<ActiveSessionDto>>>()
    )

    override operator fun invoke(
        serviceGroup: String,
        agentId: String,
        activeSessions: List<ActiveSessionDto>
    ): List<ActiveSessionDto>? {
        val sessions = _groups.updateAndGet {
            val sessionGroups = it[serviceGroup] ?: persistentMapOf()
            it.put(serviceGroup, sessionGroups.put(agentId, activeSessions))
        }
        return sessions[serviceGroup]?.values?.flatten()
    }
}
