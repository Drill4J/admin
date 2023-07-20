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
package com.epam.drill.plugins.test2code.storage

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.dsm.*
import kotlinx.serialization.*

@Serializable
internal data class Risk(
    val method: Method,
    val buildStatuses: Map<String, RiskStat> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Risk

        if (method != other.method) return false

        return true
    }

    override fun hashCode(): Int {
        return method.hashCode()
    }
}

@Serializable
internal data class BaselineRisks(
    @Id
    val baseline: AgentKey,
    val risks: Set<Risk> = emptySet(),
)

@Serializable
internal data class RiskStat(
    val coverage: Count = zeroCount,
    val status: RiskStatus = RiskStatus.NOT_COVERED,
)


internal suspend fun StoreClient.loadRisksByBaseline(
    baseline: AgentKey,
) = findById(baseline) ?: store(BaselineRisks(baseline))
