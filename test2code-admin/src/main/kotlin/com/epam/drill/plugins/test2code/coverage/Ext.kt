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
package com.epam.drill.plugins.test2code.coverage

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.util.*
import java.util.stream.*
import kotlin.math.*
import kotlinx.serialization.*

internal fun ExecClassData.id(): Long = id ?: className.crc64()

internal fun List<Boolean>.toCount() = Count(count { it }, size)

internal fun <T> List<T>.slice(probeRange: ProbeRange): List<T> = slice(probeRange.first..probeRange.last)

internal fun Count.percentage(): Double = covered percentOf total

internal fun Count?.arrowType(other: Count): ArrowType = this?.run {
    (this.subtraction(other)).first.sign.toArrowType()
} ?: ArrowType.UNCHANGED

internal infix fun Count.subtraction(other: Count): Pair<Long, Long> = takeIf { other.total > 0 }?.run {
    total.gcd(other.total).let { gcd ->
        val (totalLong, otherTotalLong) = total.toLong() to other.total.toLong()
        Pair(
            first = (otherTotalLong / gcd * covered) - (totalLong / gcd * other.covered),
            second = totalLong / gcd * otherTotalLong
        )
    }
} ?: (covered.toLong() to total.toLong())

internal fun NamedCounter.hasCoverage(): Boolean = count.covered > 0

internal fun NamedCounter.coverageKey(parent: NamedCounter? = null): CoverageKey = when (this) {
    is MethodCounter -> CoverageKey(
        id = (parent as? ClassCounter)?.run { fullClassname(path, name) }
            .let { fullMethodName(it ?: "", name, desc).crc64 },
        packageName = (parent as? ClassCounter)?.path?.weakIntern() ?: "",
        className = (parent as? ClassCounter)?.name?.weakIntern() ?: "",
        methodName = name.weakIntern(),
        methodDesc = desc.weakIntern()
    )
    is ClassCounter -> CoverageKey(
        id = fullClassname(path, name).crc64,
        packageName = path.weakIntern(),
        className = name.weakIntern()
    )
    is PackageCounter -> CoverageKey(
        id = name.crc64,
        packageName = name.weakIntern()
    )
    else -> CoverageKey(name.crc64)
}

internal fun BundleCounter.coverageKeys(
    onlyPackages: Boolean = true,
): Stream<CoverageKey> = packages.stream().flatMap { p ->
    Stream.concat(Stream.of(p.coverageKey()), p.classes.takeIf { !onlyPackages }?.stream()?.flatMap { c ->
        Stream.concat(Stream.of(c.coverageKey()), c.methods.stream().map { m ->
            m.takeIf { it.count.covered > 0 }?.coverageKey(c)
        }.filter { it != null })
    } ?: Stream.empty())
}

internal fun BundleCounter.toCoverDto(
    tree: PackageTree,
) = count.copy(total = tree.totalCount).let { count ->
    CoverDto(
        percentage = count.percentage(),
        methodCount = methodCount.copy(total = tree.totalMethodCount).toDto(),
        count = count.toDto()
    )
}

internal fun CoverContext.toCoverMap(
    bundle: BundleCounter,
    onlyCovered: Boolean,
): Map<Method, CoverMethod> = bundle.packages.let { packages ->
    val map = packages.parallelStream().flatMap { it.classes.stream() }.flatMap { c ->
        c.methods.stream().map { m -> m.fullName to m }
    }.collect(Collectors.toMap({ it.first }, { it.second }, { first, _ -> first }))
    methods.parallelStream().map { method ->
        val covered = method.toCovered(methodType(method), map[method.key])
        covered.takeIf { !onlyCovered || it.count.covered > 0 }?.let { method to it }
    }.filter { it?.first != null }.collect(Collectors.toMap({ it!!.first }, { it!!.second }, { first, _ -> first }))
}

internal fun CoverContext.methodType(method: Method) = when (method) {
    in methodChanges.modified -> MethodType.MODIFIED
    in methodChanges.new -> MethodType.NEW
    in methodChanges.deleted -> MethodType.DELETED
    else -> MethodType.UNAFFECTED
}

internal fun BundleCounter.coveredMethods(
    methods: Iterable<Method>,
): Map<Method, Count> = packages.asSequence().takeIf { p ->
    p.any { it.classes.any() }
}?.run {
    toCoveredMethods(
        { methods.groupBy(Method::ownerClass) },
        { methods.toPackageSet() }
    ).toMap()
}.orEmpty()

internal fun Sequence<PackageCounter>.toCoveredMethods(
    methodMapPrv: () -> Map<String, List<Method>>,
    packageSetPrv: () -> Set<String>,
): Sequence<Pair<Method, Count>> = takeIf { it.any() }?.run {
    val packageSet = packageSetPrv()
    filter { it.name in packageSet && it.hasCoverage() }.run {
        val methodMap = methodMapPrv()
        flatMap {
            it.classes.asSequence().filter(NamedCounter::hasCoverage)
        }.mapNotNull { c ->
            methodMap[c.fullName]?.run {
                val covered: Map<String, MethodCounter> = c.methods.asSequence()
                    .filter(NamedCounter::hasCoverage)
                    .associateBy(MethodCounter::sign)
                mapNotNull { m -> covered[m.signature]?.let { m to it.count } }.asSequence()
            }
        }.flatten()
    }
}.orEmpty()

internal fun Iterable<Method>.toPackageSet(): Set<String> = takeIf { it.any() }?.run {
    mapTo(mutableSetOf()) { method ->
        method.ownerClass.takeIf { '/' in it }?.substringBeforeLast('/').orEmpty().weakIntern()
    }
}.orEmpty()

internal fun Method.toCovered(methodType: MethodType, count: Count?) = CoverMethod(
    ownerClass = ownerClass.weakIntern(),
    name = name.weakIntern(),
    desc = desc.weakIntern(),//.takeIf { "):" in it } ?: declaration(desc), //TODO js methods //Regex has a big impact on performance
    hash = hash.weakIntern(),
    count = count?.toDto() ?: zeroCount.toDto(),
    coverage = count?.percentage() ?: 0.0,
    type = methodType,
    coverageRate = count?.coverageRate() ?: CoverageRate.MISSED
)

internal fun Method.toCovered(
    methodType: MethodType,
    counter: MethodCounter? = null,
): CoverMethod = toCovered(methodType, counter?.count)

/**
 * A pair of test ID and test type
 * @param id the test ID
 * @param type the test type
 */
@Serializable
data class TestKey(
    val id: String,
    val type: String,
)

internal fun String.testKey(type: String) = TestKey(
    id = this,
    type = type
)

internal fun TestDetails.typedTest(type: String) = TypedTest(
    details = this,
    type = type,
)

fun TestKey.id() = "$id:$type".weakIntern()

private fun Int.toArrowType(): ArrowType? = when (this) {
    in Int.MIN_VALUE..-1 -> ArrowType.INCREASE
    in 1..Int.MAX_VALUE -> ArrowType.DECREASE
    else -> null
}

//TODO remove
internal fun Count.coverageRate() = when (covered) {
    0 -> CoverageRate.MISSED
    in 1 until total -> CoverageRate.PARTLY
    else -> CoverageRate.FULL
}
