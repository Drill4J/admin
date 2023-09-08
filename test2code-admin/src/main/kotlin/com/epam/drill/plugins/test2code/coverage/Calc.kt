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
    val classIdToClassName: Map<String, String> = tree.packages
        .flatMap { it.classes }
        .map { fullClassname(it.path, it.name) }
        .associateBy(
            keySelector = { it.crc64().toString() },
            valueTransform = { it }
        )
    val classIdToProbeCounts: Map<String, Int> = tree.packages
        .flatMap { it.classes }
        .associateBy(
            keySelector = { fullClassname(it.path, it.name).crc64().toString() },
            valueTransform = { it.totalCount }
        )
    val classIdToProbes: Map<String, List<Boolean>> = this.filter { it.id.toString() in classIdToProbeCounts }
        .groupBy { it.id.toString() }
        .mapValues { (classId, execDataList) ->
            val initialProbe = BooleanArray(classIdToProbeCounts.getValue(classId)) { false }.toList()
            execDataList.map(ExecClassData::probes).fold(initialProbe) { acc, probes ->
                acc.merge(probes.toList())
            }
        }
    val classIdToMethods = tree.packages
        .flatMap { it.classes }
        .associate {
            fullClassname(it.path, it.name).crc64().toString() to it.methods
        }
    val covered = classIdToProbes.values.sumOf { probes -> probes.count { it } }
    val packages = classIdToProbes.keys
        .groupBy { classId -> classPath(classIdToClassName[classId]!!) }
        .map { (pkgName, classIds) ->
            val classes = classIds.map { classId ->
                val probes = classIdToProbes.getValue(classId)
                ClassCounter(
                    path = pkgName.weakIntern(),
                    name = classname(classIdToClassName[classId]!!),
                    count = probes.toCount(),
                    fullName = classIdToClassName[classId]!!,
                    probes = probes,
                    methods = classIdToMethods.getValue(classId).map {
                        val methodProbes = probes.slice(it.probeRange)
                        val sign = signature(classId, it.name, it.desc)
                        MethodCounter(
                            it.name, it.desc, it.decl,
                            sign = sign,
                            fullName = fullMethodName(classId, it.name, it.desc),
                            count = methodProbes.toCount()
                        )
                    }
                )
            }
            PackageCounter(
                name = pkgName,
                count = classIds.flatMap { classIdToProbes[it] ?: emptyList() }.toCount(),
                classCount = Count(
                    classIds.count { id -> classIdToProbes.getValue(id).any { it } },
                    classIds.size
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
