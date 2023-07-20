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

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.*
import kotlinx.serialization.*

/**
 * Method model
 * @param ownerClass the method's class name
 * @param name the method's name
 * @param desc the description
 * @param hash the hash of the method body
 * @param lambdasHash the hash of the lambda
 */
@Serializable
data class Method(
    val ownerClass: String,
    val name: String,
    val desc: String,
    val hash: String,
    val lambdasHash: Map<String, String> = emptyMap(),
) : Comparable<Method> {
    val signature = signature(ownerClass, name, desc).intern()
    val key = fullMethodName(ownerClass, name, desc).intern()
    override fun compareTo(
        other: Method,
    ): Int = ownerClass.compareTo(other.ownerClass).takeIf {
        it != 0
    } ?: name.compareTo(other.name).takeIf {
        it != 0
    } ?: desc.compareTo(other.desc)
}

internal typealias TypedRisks = Map<RiskType, List<Risk>>

/**
 * Methods are sorted to improve performance of difference calculation
 */
internal fun List<Method>.diff(otherMethods: List<Method>): DiffMethods = if (any()) {
    if (otherMethods.any()) {
        val new = mutableListOf<Method>()
        val modified = mutableListOf<Method>()
        val deleted = mutableListOf<Method>()
        val unaffected = mutableListOf<Method>()
        val otherItr = otherMethods.sorted().iterator()
        sorted().iterator().run {
            var lastRight: Method? = otherItr.next()
            while (hasNext()) {
                val left = next()
                if (lastRight == null) {
                    new.add(left)
                }
                while (lastRight != null) {
                    val right = lastRight
                    val cmp = left.compareTo(right)
                    if (cmp <= 0) {
                        when {
                            cmp == 0 -> {
                                (unaffected.takeIf {
                                    left.hash == right.hash
                                            && left.lambdasHash.all { right.lambdasHash.containsValue(it.value) }
                                } ?: modified).add(left)
                                lastRight = otherItr.nextOrNull()
                            }
                            cmp < 0 -> {
                                new.add(left)
                            }
                        }
                        break
                    }
                    deleted.add(right)
                    lastRight = otherItr.nextOrNull()
                    if (lastRight == null) {
                        new.add(left)
                    }
                }
            }
            lastRight?.let { deleted.add(it) }
            while (otherItr.hasNext()) {
                deleted.add(otherItr.next())
            }
        }
        DiffMethods(
            new = new,
            modified = modified,
            deleted = deleted,
            unaffected = unaffected
        )
    } else DiffMethods(new = this)
} else DiffMethods(deleted = otherMethods)


internal fun BuildMethods.toSummaryDto() = MethodsSummaryDto(
    all = totalMethods.run { Count(coveredCount, totalCount).toDto() },
    new = newMethods.run { Count(coveredCount, totalCount).toDto() },
    modified = allModifiedMethods.run { Count(coveredCount, totalCount).toDto() },
    unaffected = unaffectedMethods.run { Count(coveredCount, totalCount).toDto() },
    deleted = deletedMethods.run { Count(coveredCount, totalCount).toDto() }
)

/**
 * 1. Check whether new/modified methods have coverage
 * 2. Store covered/uncovered methods for this baseline (for example method «foo» was covered in 0.2.0 build for 0.1.0 baseline)
 * 3. Filter risks for current build compare with stored baseline risks
 */
internal suspend fun CoverContext.calculateRisks(
    storeClient: StoreClient,
    bundleCounter: BundleCounter = build.bundleCounters.all,
): TypedRisks = bundleCounter.coveredMethods(methodChanges.new + methodChanges.modified).let { covered ->
    trackTime("Risk calculation") {
        val buildVersion = build.agentKey.buildVersion
        val baselineBuild = parentBuild?.agentKey ?: build.agentKey

        val baselineCoveredRisks = storeClient.loadRisksByBaseline(baselineBuild)
        val riskByMethod = baselineCoveredRisks.risks.mapNotNull { risk ->
            risk.copy(buildStatuses = risk.buildStatuses - buildVersion).takeIf { it.buildStatuses.isNotEmpty() }
        }.associateByTo(mutableMapOf()) { it.method }
        val newCovered = methodChanges.new.filterByCoverage(covered)
        val modifiedCovered = methodChanges.modified.filterByCoverage(covered)

        riskByMethod.putRisks(newCovered + modifiedCovered, buildVersion)

        storeClient.store(baselineCoveredRisks.copy(risks = riskByMethod.values.toSet()))

        mapOf(
            RiskType.NEW to methodChanges.new.map { riskByMethod[it] ?: Risk(it) },
            RiskType.MODIFIED to methodChanges.modified.map { riskByMethod[it] ?: Risk(it) }
        )
    }
}

internal fun List<Method>.filterByCoverage(
    covered: Map<Method, Count>,
) = filter { it in covered }.map { it to (covered[it] ?: zeroCount) }

internal fun MutableMap<Method, Risk>.putRisks(
    methods: List<Pair<Method, Count>>,
    buildVersion: String,
    status: RiskStatus = RiskStatus.COVERED,
) = methods.forEach { (method, coverage) ->
    get(method)?.let {
        put(method, it.copy(buildStatuses = it.buildStatuses + (buildVersion to RiskStat(coverage, status))))
    } ?: put(method, Risk(method, mapOf(buildVersion to RiskStat(coverage, status))))
}

internal fun TypedRisks.toCounts() = RiskCounts(
    new = this[RiskType.NEW]?.count() ?: 0,
    modified = this[RiskType.MODIFIED]?.count() ?: 0
).run { copy(total = new + modified) }

internal fun TypedRisks.notCovered() = asSequence().mapNotNull { (type, risks) ->
    val uncovered = risks.filter { risk -> RiskStatus.COVERED !in risk.buildStatuses.values.map { it.status } }
    uncovered.takeIf { it.any() }?.let { type to it }
}.toMap()

internal fun TypedRisks.count() = values.sumOf { it.count() }

internal suspend fun CoverContext.risksDto(
    storeClient: StoreClient,
    associatedTests: Map<CoverageKey, List<TypedTest>> = emptyMap(),
): List<RiskDto> = run {
    val buildVersion = build.agentKey.buildVersion
    calculateRisks(storeClient).flatMap { (type, risks) ->
        risks.map { risk ->
            val currentCoverage = risk.buildStatuses[buildVersion]?.coverage ?: zeroCount
            val id = risk.method.key.crc64
            RiskDto(
                id = id,
                type = type,
                ownerClass = risk.method.ownerClass,
                name = risk.method.name,
                desc = risk.method.desc,
                coverage = currentCoverage.percentage(),
                count = currentCoverage.toDto(),
                previousCovered = risk.previousRiskCoverage(buildVersion),
                coverageRate = currentCoverage.coverageRate(),
                assocTestsCount = associatedTests[CoverageKey(id)]?.count() ?: 0,
            )
        }
    }
}

private fun Risk.previousRiskCoverage(
    buildVersion: String,
) = buildStatuses.filter { it.key != buildVersion }.entries.fold(null) { statDto: RiskStatDto?, (build, coverage) ->
    val percentage = coverage.coverage.percentage()
    statDto?.let {
        it.takeIf { it.coverage >= percentage } ?: RiskStatDto(build, percentage)
    } ?: RiskStatDto(build, percentage)
}


internal fun Map<Method, CoverMethod>.toSummary(
    id: String,
    typedTest: TypedTest,
    context: CoverContext,
) = TestedMethodsSummary(
    id = id,
    details = typedTest.details,
    testType = typedTest.type,
    methodCounts = CoveredMethodCounts(
        all = size,
        modified = context.methodChanges.modified.count { it in this },
        new = context.methodChanges.new.count { it in this },
        unaffected = context.methodChanges.unaffected.count { it in this }
    )
)

internal fun Map<Method, CoverMethod>.filterValues(
    predicate: (Method) -> Boolean,
) = filter { predicate(it.key) }.values.toList()

private fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) {
    next()
} else null
