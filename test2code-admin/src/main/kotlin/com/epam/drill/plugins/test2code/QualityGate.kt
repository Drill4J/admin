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

import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.routes.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.group.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.*
import kotlinx.serialization.*
import kotlin.reflect.*

private object QualityGateData {
    @Serializable
    data class IdByAgent(
        val agentId: String,
        val conditionType: String,
    )

    @Serializable
    data class AgentSetting(
        @Id val id: IdByAgent,
        val setting: ConditionSetting,
    )

    private val pairs: List<Pair<KProperty1<StatsDto, Number>, QualityGateCondition>> = listOf(
        StatsDto::coverage.run { this to toCondition(operator = ConditionOp.GTE, value = 0.0) },
        StatsDto::risks.run { this to toCondition(operator = ConditionOp.LTE, value = 1.0) },
        StatsDto::tests.run { this to toCondition(operator = ConditionOp.LTE, value = 1.0) }
    )

    val defaults: Map<String, QualityGateCondition> = pairs.associate {
        it.first.name to it.second
    }

    val properties: Map<String, KProperty1<StatsDto, Number>> = pairs.associate {
        it.first.name to it.first
    }
}

/**
 * Initialize state of quality gate settings from DB
 *
 * @features Agent registration
 */
internal suspend fun Plugin.initGateSettings() {
    val settings = state.qualityGateSettings
    QualityGateData.defaults.forEach { (key, value) ->
        settings[key] = ConditionSetting(false, value)
    }
    storeClient.getAll<QualityGateData.AgentSetting>().forEach {
        settings[it.id.conditionType] = it.setting
    }
}

internal suspend fun Plugin.updateGateConditions(
    conditionSettings: List<ConditionSetting>,
): ActionResult = run {
    val settings = state.qualityGateSettings
    val unknownMeasures = conditionSettings.filter { it.condition.measure !in settings.map }
    if (unknownMeasures.none()) {
        conditionSettings.forEach { settings[it.condition.measure] = it }
        sendGateSettings()
        state.toStatsDto().let { stats ->
            val qualityGate = checkQualityGate(stats)
            send(buildVersion, Routes.Data().let(Routes.Data::QualityGate), qualityGate)
        }
        storeClient.executeInAsyncTransaction {
            conditionSettings.forEach { setting ->
                val measure = setting.condition.measure
                val id = QualityGateData.IdByAgent(agentId, measure)
                store(QualityGateData.AgentSetting(id, setting))
            }
        }
        ActionResult(StatusCodes.OK, "")
    } else ActionResult(StatusCodes.BAD_REQUEST, "Unknown quality gate measures: '$unknownMeasures'")
}

/**
 * Send quality gate settings to the UI
 * @features Agent registration
 */
internal suspend fun Plugin.sendGateSettings() {
    val dataRoute = Routes.Data()
    val settings = state.qualityGateSettings.values.toList()
    send(buildVersion, dataRoute.let(Routes.Data::QualityGateSettings), settings)
}

internal fun Plugin.checkQualityGate(stats: StatsDto): QualityGate = run {
    val conditions = state.qualityGateSettings.values
        .filter { it.enabled }
        .map { it.condition }
    val checkResults = conditions.associate { it.measure to stats.check(it) }
    val status = when (checkResults.values.toSet()) {
        emptySet<Boolean>(), setOf(true) -> GateStatus.PASSED
        else -> GateStatus.FAILED
    }
    QualityGate(
        status = status,
        results = checkResults
    )
}

private suspend fun AgentState.toStatsDto(): StatsDto = coverContext().run {
    build.toSummary(
        agentInfo.name,
        testsToRun,
        coverContext().calculateRisks(storeClient)
    )
}.toStatsDto()

internal fun AgentSummary.toStatsDto() = StatsDto(
    coverage = coverage.percentage(),
    risks = riskCounts.total,
    tests = testsToRun.totalCount()
)

private fun <T : Number> KProperty1<StatsDto, T>.toCondition(
    operator: ConditionOp,
    value: Number,
): QualityGateCondition = QualityGateCondition(
    measure = name,
    operator = operator,
    value = value.toDouble()
)


private fun StatsDto.check(condition: QualityGateCondition): Boolean = run {
    val value = QualityGateData.properties.getValue(condition.measure).get(this).toDouble()
    when (condition.operator) {
        ConditionOp.LT -> value < condition.value
        ConditionOp.LTE -> value <= condition.value
        ConditionOp.GT -> value > condition.value
        ConditionOp.GTE -> value >= condition.value
    }
}
