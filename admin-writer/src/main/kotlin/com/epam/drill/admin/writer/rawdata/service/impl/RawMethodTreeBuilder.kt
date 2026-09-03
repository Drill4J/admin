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
package com.epam.drill.admin.writer.rawdata.service.impl

import com.epam.drill.admin.writer.rawdata.views.RAW_METHOD_TREE_TYPE_CLASS
import com.epam.drill.admin.writer.rawdata.views.RAW_METHOD_TREE_TYPE_METHOD
import com.epam.drill.admin.writer.rawdata.views.RAW_METHOD_TREE_TYPE_PACKAGE
import com.epam.drill.admin.writer.rawdata.views.RawMethodLeaf
import com.epam.drill.admin.writer.rawdata.views.RawMethodTreeNodeView
import com.epam.drill.admin.writer.rawdata.views.RawMethodTreeView

private const val DEFAULT_PACKAGE_KEY = ""
private const val DEFAULT_PACKAGE_LABEL = "(default package)"

/**
 * Builds a nested package→class→method tree from method leaves (same nesting/collapse rules as
 * coverage treemap). Sized for structure display (each method contributes at least 1).
 */
internal fun buildRawMethodTree(methods: List<RawMethodLeaf>): RawMethodTreeView {
    val nodeMap = mutableMapOf<String, MutableMap<String, Any?>>()
    val rootPaths = mutableSetOf<String>()
    var affected = 0L

    for (item in methods) {
        if (item.ignored) affected++
        val methodLabel = "${item.methodName}(${item.methodParams}) -> ${item.returnType}"
        val pathParts = (item.className.split("/") + methodLabel)
        val methodIndex = pathParts.lastIndex
        val classIndex = pathParts.lastIndex - 1
        var currentPath = ""

        pathParts.forEachIndexed { index, part ->
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
            val nodeType = when (index) {
                methodIndex -> RAW_METHOD_TREE_TYPE_METHOD
                classIndex -> RAW_METHOD_TREE_TYPE_CLASS
                else -> RAW_METHOD_TREE_TYPE_PACKAGE
            }
            if (!nodeMap.containsKey(currentPath)) {
                val node = mutableMapOf<String, Any?>(
                    "name" to part,
                    "full_name" to currentPath,
                    "type" to nodeType,
                    "package_name" to when (nodeType) {
                        RAW_METHOD_TREE_TYPE_PACKAGE -> currentPath
                        else -> item.packageName
                    },
                    "class_name" to when (nodeType) {
                        RAW_METHOD_TREE_TYPE_PACKAGE -> null
                        else -> item.className
                    },
                    "probes_count" to 0L,
                    "covered_probes" to 0L,
                    "ignored" to false,
                    "total_methods" to 0,
                    "ignored_methods" to 0,
                    "children" to mutableSetOf<String>(),
                    "parent" to if (index == 0) null else pathParts.subList(0, index).joinToString("/"),
                )
                if (nodeType == RAW_METHOD_TREE_TYPE_METHOD) {
                    node["method_id"] = item.methodId
                    node["method_name"] = item.methodName
                    node["signature"] = item.signature
                    node["params"] = item.methodParams
                    node["return_type"] = item.returnType
                }
                nodeMap[currentPath] = node
            }
            if (index > 0) {
                val parentPath = pathParts.subList(0, index).joinToString("/")
                (nodeMap.getValue(parentPath)["children"] as MutableSet<String>).add(currentPath)
            } else {
                rootPaths.add(currentPath)
            }
        }

        val size = maxOf(1L, item.probesCount.toLong())
        val leaf = nodeMap.getValue(currentPath)
        leaf["probes_count"] = size
        leaf["covered_probes"] = if (item.ignored) 0L else size
        leaf["ignored"] = item.ignored
        leaf["total_methods"] = 1
        leaf["ignored_methods"] = if (item.ignored) 1 else 0
        leaf["method_id"] = item.methodId
        leaf["method_name"] = item.methodName
        leaf["signature"] = item.signature
        leaf["params"] = item.methodParams
        leaf["return_type"] = item.returnType
    }

    val collapsedNodeMap = mutableMapOf<String, MutableMap<String, Any?>>()

    fun collapseAndCopy(path: String, parentPath: String?): String {
        var node = nodeMap.getValue(path)
        var name = node["name"] as String
        var fullName = path
        var children = node["children"] as Set<String>

        // Only collapse consecutive single-child package nodes (same as coverage TreemapBuilder).
        while (children.size == 1) {
            val childPath = children.first()
            val child = nodeMap[childPath] ?: break
            if (child["type"] != RAW_METHOD_TREE_TYPE_PACKAGE) break
            name = "$name/${child["name"]}"
            fullName = child["full_name"] as String
            node = child
            children = child["children"] as Set<String>
        }

        val newNode = mutableMapOf(
            "name" to name,
            "full_name" to fullName,
            "type" to node["type"],
            "parent" to parentPath,
            "package_name" to when (node["type"]) {
                RAW_METHOD_TREE_TYPE_PACKAGE -> fullName
                else -> node["package_name"]
            },
            "class_name" to node["class_name"],
            "method_id" to node["method_id"],
            "method_name" to node["method_name"],
            "signature" to node["signature"],
            "params" to node["params"],
            "return_type" to node["return_type"],
            "probes_count" to node["probes_count"] as Long,
            "covered_probes" to node["covered_probes"] as Long,
            "ignored" to node["ignored"] as Boolean,
            "total_methods" to node["total_methods"] as Int,
            "ignored_methods" to node["ignored_methods"] as Int,
            "children" to mutableSetOf<String>(),
        )
        collapsedNodeMap[fullName] = newNode
        for (child in children) {
            val collapsedChild = collapseAndCopy(child, fullName)
            (newNode["children"] as MutableSet<String>).add(collapsedChild)
        }
        return fullName
    }

    val newRoots = rootPaths.map { collapseAndCopy(it, null) }.toMutableSet()

    fun computeAggregates(path: String) {
        val node = collapsedNodeMap.getValue(path)
        val children = node["children"] as Set<String>
        if (children.isEmpty()) return
        var probes = 0L
        var covered = 0L
        var total = 0
        var ignored = 0
        for (child in children) {
            computeAggregates(child)
            val childNode = collapsedNodeMap.getValue(child)
            probes += childNode["probes_count"] as Long
            covered += childNode["covered_probes"] as Long
            total += childNode["total_methods"] as Int
            ignored += childNode["ignored_methods"] as Int
        }
        node["probes_count"] = probes
        node["covered_probes"] = covered
        node["total_methods"] = total
        node["ignored_methods"] = ignored
        node["ignored"] = ignored > 0 && ignored == total
    }

    newRoots.forEach(::computeAggregates)

    fun serializeNode(path: String): RawMethodTreeNodeView {
        val node = collapsedNodeMap.getValue(path)
        val children = (node["children"] as Set<String>)
            .map(::serializeNode)
            .sortedBy { it.name }
        return RawMethodTreeNodeView(
            type = node["type"] as String,
            name = node["name"] as String,
            fullName = node["full_name"] as String,
            packageName = node["package_name"] as? String ?: "",
            className = node["class_name"] as? String,
            methodId = node["method_id"] as? String,
            methodName = node["method_name"] as? String,
            signature = node["signature"] as? String,
            params = node["params"] as? String,
            returnType = node["return_type"] as? String,
            probesCount = node["probes_count"] as Long,
            coveredProbes = node["covered_probes"] as Long,
            ignored = node["ignored"] as Boolean,
            totalMethods = node["total_methods"] as Int,
            ignoredMethods = node["ignored_methods"] as Int,
            children = children,
        )
    }

    val serializedRoots = newRoots.map(::serializeNode)
    val classRoots = serializedRoots.filter { it.type == RAW_METHOD_TREE_TYPE_CLASS }
    val packageRoots = serializedRoots.filter { it.type == RAW_METHOD_TREE_TYPE_PACKAGE }
    val roots = if (classRoots.isEmpty()) {
        packageRoots.sortedBy { it.name }
    } else {
        val defaultPackage = RawMethodTreeNodeView(
            type = RAW_METHOD_TREE_TYPE_PACKAGE,
            name = DEFAULT_PACKAGE_LABEL,
            fullName = DEFAULT_PACKAGE_LABEL,
            packageName = DEFAULT_PACKAGE_KEY,
            probesCount = classRoots.sumOf { it.probesCount },
            coveredProbes = classRoots.sumOf { it.coveredProbes },
            ignored = classRoots.isNotEmpty() && classRoots.all { it.ignored },
            totalMethods = classRoots.sumOf { it.totalMethods },
            ignoredMethods = classRoots.sumOf { it.ignoredMethods },
            children = classRoots.sortedBy { it.name },
        )
        (packageRoots + defaultPackage).sortedBy { it.name }
    }

    return RawMethodTreeView(
        roots = roots,
        affectedMethods = affected,
        totalMethods = methods.size.toLong(),
    )
}
