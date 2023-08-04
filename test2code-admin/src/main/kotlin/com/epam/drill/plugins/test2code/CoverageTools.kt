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
import com.epam.drill.plugins.test2code.util.*
import java.util.stream.*

//TODO Rewrite all of this, remove the file

/**
 * Information about method covering of the build version
 * @param totalMethods overall information
 * @param newMethods information only by new methods
 * @param modifiedNameMethods information only by modified method names
 * @param modifiedDescMethods information only by modified method descriptors
 * @param allModifiedMethods information by all modified methods
 * @param unaffectedMethods information by unaffected methods
 * @param deletedMethods information by deleted methods
 */
internal data class BuildMethods(
    val totalMethods: MethodsInfo = MethodsInfo(),
    val newMethods: MethodsInfo = MethodsInfo(),
    val modifiedNameMethods: MethodsInfo = MethodsInfo(),
    val modifiedDescMethods: MethodsInfo = MethodsInfo(),
    val modifiedBodyMethods: MethodsInfo = MethodsInfo(),
    val allModifiedMethods: MethodsInfo = MethodsInfo(),
    val unaffectedMethods: MethodsInfo = MethodsInfo(),
    val deletedMethods: MethodsInfo = MethodsInfo(),
)

/**
 * Various sets of coverage information
 */
internal data class CoverageInfoSet(
    val associatedTests: Map<CoverageKey, List<TypedTest>>,
    val coverage: Coverage,
    val buildMethods: BuildMethods = BuildMethods(),
    val packageCoverage: List<JavaPackageCoverage> = emptyList(),
    val tests: List<TestCoverageDto> = emptyList(),
    val coverageByTests: CoverageByTests,
)

fun Map<CoverageKey, List<TypedTest>>.getAssociatedTests(): List<AssociatedTests> = trackTime("associatedTests") {
    entries.parallelStream().map { (key, tests) ->
        AssociatedTests(
            id = key.id,
            packageName = key.packageName,
            className = key.className,
            methodName = key.methodName,
            tests = tests.stream().sorted { o1, o2 -> o1.details.compareTo(o2.details) }.collect(Collectors.toList())
        )
    }.sorted { o1, o2 -> o1.methodName.compareTo(o2.methodName) }.collect(Collectors.toList())
}

internal fun CoverContext.calculateBundleMethods(
    bundleCoverage: BundleCounter,
    onlyCovered: Boolean = false,
): BuildMethods = toCoverMap(bundleCoverage, onlyCovered).let { covered ->
    methodChanges.run {
        BuildMethods(
            totalMethods = covered.keys.toInfo(covered),
            newMethods = new.toInfo(covered),
            allModifiedMethods = modified.toInfo(covered),
            unaffectedMethods = unaffected.toInfo(covered),
            deletedMethods = MethodsInfo(
                totalCount = deleted.count(),
                coveredCount = deletedWithCoverage.count(),
                methods = deleted.map { it.toCovered(MethodType.DELETED, deletedWithCoverage[it]) }
            )
        )
    }
}

private fun Iterable<Method>.toInfo(
    covered: Map<Method, CoverMethod>,
) = MethodsInfo(
    totalCount = count(),
    coveredCount = count { (covered[it]?.count?.covered ?: 0) > 0 },
    methods = mapNotNull(covered::get)
)
