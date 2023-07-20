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
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.util.*

/**
 * Build collection of packages from ast collection
 * @return list of java packages
 * @features Agent registration
 */
internal fun Iterable<AstEntity>.toPackages(): List<JavaPackageCoverage> = run {
    groupBy(AstEntity::path).entries.map { (path, astEntities) ->
        JavaPackageCoverage(
            id = path.crc64,
            name = path.weakIntern(),
            totalClassesCount = astEntities.count(),
            totalMethodsCount = astEntities.flatMap(AstEntity::methodsWithProbes).count(),
            totalCount = astEntities.flatMap(AstEntity::methodsWithProbes).sumOf(AstMethod::count),
            classes = astEntities.mapNotNull { ast ->
                ast.methodsWithProbes().takeIf { it.any() }?.let { methods ->
                    val className = fullClassname(path, ast.name)
                    JavaClassCoverage(
                        id = className.crc64,
                        name = ast.name.weakIntern(),
                        path = path.weakIntern(),
                        totalMethodsCount = methods.count(),
                        totalCount = methods.sumOf { it.count },
                        methods = methods.fold(listOf()) { acc, astMethod ->
                            val desc = astMethod.toDesc()
                            acc + JavaMethodCoverage(
                                id = fullMethodName(className, astMethod.name, desc).crc64,
                                name = astMethod.name.weakIntern(),
                                desc = desc,
                                probesCount = astMethod.probes.size,
                                decl = desc,
                                probeRange = (acc.lastOrNull()?.probeRange?.last?.inc() ?: 0).let {
                                    ProbeRange(it, it + astMethod.probes.lastIndex)
                                }
                            )
                        },
                    )
                }
            }
        )
    }
}

internal fun Iterable<PackageCounter>.toPackages(
    parsedClasses: Map<String, List<Method>>
): List<JavaPackageCoverage> = mapNotNull { packageCoverage ->
    packageCoverage.classes.classTree(parsedClasses).takeIf { it.any() }?.let { classes ->
        JavaPackageCoverage(
            id = packageCoverage.coverageKey().id,
            name = packageCoverage.name,
            totalClassesCount = classes.count(),
            totalMethodsCount = classes.sumOf { it.totalMethodsCount },
            totalCount = packageCoverage.count.total,
            classes = classes
        )
    }
}.toList()


internal fun Iterable<JavaPackageCoverage>.treeCoverage(
    bundle: BundleCounter,
    assocTestsMap: Map<CoverageKey, List<TypedTest>>
): List<JavaPackageCoverage> = run {
    val bundleMap = bundle.packages.associateBy { it.coverageKey().id }
    map { pkg ->
        bundleMap[pkg.id]?.run {
            pkg.copy(
                coverage = count.copy(total = pkg.totalCount).percentage(),
                coveredClassesCount = classCount.covered,
                coveredMethodsCount = methodCount.covered,
                assocTestsCount = assocTestsMap[coverageKey()]?.count() ?: 0,
                classes = pkg.classes.classCoverage(classes, assocTestsMap)
            )
        } ?: pkg
    }
}

private fun Collection<ClassCounter>.classTree(
    parsedClasses: Map<String, List<Method>>
): List<JavaClassCoverage> = mapNotNull { classCoverage ->
    parsedClasses[classCoverage.fullName]?.let { parsedMethods ->
        val classKey = classCoverage.coverageKey()
        val methods = classCoverage.toMethodCoverage { methodCov ->
            parsedMethods.any { it.signature == methodCov.sign }
        }
        JavaClassCoverage(
            id = classKey.id,
            name = classname(classCoverage.name),
            path = classCoverage.path,
            totalMethodsCount = methods.count(),
            totalCount = methods.sumOf { it.probesCount },
            methods = methods,
        )
    }
}.toList()

private fun List<JavaClassCoverage>.classCoverage(
    classCoverages: Collection<ClassCounter>,
    assocTestsMap: Map<CoverageKey, List<TypedTest>>
): List<JavaClassCoverage> = run {
    val bundleMap = classCoverages.associateBy { it.coverageKey().id }
    map { classCov ->
        bundleMap[classCov.id]?.run {
            classCov.copy(
                coverage = count.percentage(),
                coveredMethodsCount = methods.count { it.count.covered > 0 },
                assocTestsCount = assocTestsMap[coverageKey()]?.count() ?: 0,
                methods = toMethodCoverage(assocTestsMap, classCov.methods),
                probes = probes,
            )
        } ?: classCov
    }
}

internal fun ClassCounter.toMethodCoverage(
    assocTestsMap: Map<CoverageKey, List<TypedTest>> = emptyMap(),
    astMethods: List<JavaMethodCoverage> = emptyList(),
    filter: (MethodCounter) -> Boolean = { true }
): List<JavaMethodCoverage> {
    val astMethodsMap = astMethods.associateBy { it.id }
    return methods.filter(filter).map { methodCoverage ->
        val methodKey = methodCoverage.coverageKey(this)
        JavaMethodCoverage(
            id = methodKey.id,
            name = methodCoverage.name,
            desc = methodCoverage.desc,
            decl = methodCoverage.decl,
            coverage = methodCoverage.count.percentage(),
            probesCount = methodCoverage.count.total,
            assocTestsCount = assocTestsMap[methodKey]?.count() ?: 0,
            probeRange = astMethodsMap[methodKey.id]?.probeRange ?: ProbeRange.EMPTY,
        )
    }.toList()
}

internal fun AstMethod.toDesc(): String = params.joinToString(
    prefix = "(", postfix = "):$returnType"
).weakIntern()

fun AstEntity.methodsWithProbes(): List<AstMethod> = methods.filter { it.probes.any() }
