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

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.util.*

/**
 * Calculate a build coverage for non java agents
 * @param tree the tree of application packages
 * @return a calculated build coverage
 * @features Scope finishing
 */
internal fun Sequence<ExecClassData>.bundle(
    tree: PackageTree
): BundleCounter = run {
    val probeCounts: Map<String, Int> = tree.packages.run {
        flatMap { it.classes }.associateBy({ fullClassname(it.path, it.name) }) { it.totalCount }
    }
    val probesByClasses: Map<String, List<Boolean>> = filter {
        it.className in probeCounts
    }.groupBy(ExecClassData::className).mapValues { (className, execDataList) ->
        val initialProbe = BooleanArray(probeCounts.getValue(className)) { false }.toList()
        execDataList.map(ExecClassData::probes).fold(initialProbe) { acc, probes ->
            acc.merge(probes.toList())
        }
    }
    val classMethods = tree.packages.flatMap { it.classes }.associate {
        fullClassname(it.path, it.name) to it.methods
    }
    val covered = probesByClasses.values.sumBy { probes -> probes.count { it } }
    val packages = probesByClasses.keys.groupBy { classPath(it) }.map { (pkgName, classNames) ->
        val classes = classNames.map { fullClassname ->
            val probes = probesByClasses.getValue(fullClassname)
            ClassCounter(
                path = pkgName.weakIntern(),
                name = classname(fullClassname),
                count = probes.toCount(),
                fullName = fullClassname,
                probes = probes,
                methods = classMethods.getValue(fullClassname).map {
                    val methodProbes = probes.slice(it.probeRange)
                    val sign = signature(fullClassname, it.name, it.desc)
                    MethodCounter(
                        it.name, it.desc, it.decl,
                        sign = sign,
                        fullName = fullMethodName(fullClassname, it.name, it.desc),
                        count = methodProbes.toCount()
                    )
                }
            )
        }
        PackageCounter(
            name = pkgName,
            count = classNames.flatMap { probesByClasses[it] ?: emptyList() }.toCount(),
            classCount = Count(
                classNames.count { name -> probesByClasses.getValue(name).any { it } },
                classNames.size
            ),
            methodCount = Count(
                classes.sumOf { c -> c.methods.count { it.count.covered > 0 } },
                classes.sumOf { it.methods.count() }
            ),
            classes = classes
        )
    }
    BundleCounter(
        name = "",
        count = Count(covered, tree.totalCount),
        methodCount = packages.run {
            Count(sumOf { it.methodCount.covered }, sumOf { it.methodCount.total })
        },
        classCount = packages.run {
            Count(sumOf { it.classCount.covered }, sumOf { it.classCount.total })
        },
        packageCount = packages.run {
            Count(count { it.classCount.covered > 0 }, count())
        },
        packages = packages
    )
}
