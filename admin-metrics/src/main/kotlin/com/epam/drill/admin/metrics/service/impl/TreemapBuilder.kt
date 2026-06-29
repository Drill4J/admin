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
package com.epam.drill.admin.metrics.service.impl

import com.epam.drill.admin.metrics.util.packageNameForPathIndex
import com.epam.drill.admin.metrics.util.packageNameFromClassName
import com.epam.drill.admin.metrics.util.simpleClassName

internal const val TREEMAP_TYPE_PACKAGE = "package"
internal const val TREEMAP_TYPE_CLASS = "class"
internal const val TREEMAP_TYPE_METHOD = "method"

internal fun buildTree(data: List<Map<String, Any?>>, rootId: String?): List<Map<String, Any?>> {
    val nodeMap = mutableMapOf<String, MutableMap<String, Any?>>()
    val rootNodes = mutableSetOf<String>()

    // Step 1: Build full uncollapsed tree
    for (item in data) {
        val className = item["class_name"] as String
        val packageNameFromClass = packageNameFromClassName(className)
        val pathParts = ("$className/${item["method_name"]}").split("/")
        var currentPath = ""
        val methodIndex = pathParts.lastIndex
        val classIndex = pathParts.lastIndex - 1

        for ((index, part) in pathParts.withIndex()) {
            var nodePart = part
            if (index == pathParts.lastIndex) {
                nodePart += "(${item["method_params"]}) -> ${item["return_type"]}"
            }

            currentPath = if (currentPath.isEmpty()) nodePart else "$currentPath/$nodePart"
            if (!rootId.isNullOrBlank() && !currentPath.startsWith(rootId)) {
                continue
            }

            val nodeType = when (index) {
                methodIndex -> TREEMAP_TYPE_METHOD
                classIndex -> TREEMAP_TYPE_CLASS
                else -> TREEMAP_TYPE_PACKAGE
            }

            if (!nodeMap.containsKey(currentPath)) {
                val node = mutableMapOf<String, Any?>(
                    "name" to nodePart,
                    "full_name" to currentPath,
                    "type" to nodeType,
                    "probes_count" to 0L,
                    "covered_probes" to 0L,
                    "children" to mutableSetOf<String>(),
                    "parent" to if (index == 0) null else pathParts.subList(0, index).joinToString("/"),
                    "params" to if (index == pathParts.lastIndex) item["method_params"] else null,
                    "return_type" to if (index == pathParts.lastIndex) item["return_type"] else null,
                )

                when (nodeType) {
                    TREEMAP_TYPE_METHOD -> {
                        node["signature"] = item["signature"]
                        node["class_name"] = className
                        node["package_name"] = packageNameFromClass
                    }
                    TREEMAP_TYPE_CLASS -> {
                        node["class_name"] = className
                        node["package_name"] = packageNameFromClass
                    }
                    TREEMAP_TYPE_PACKAGE -> {
                        node["package_name"] = packageNameForPathIndex(pathParts, index)
                    }
                }

                nodeMap[currentPath] = node
            }

            if (index > 0) {
                val parentPath = pathParts.subList(0, index).joinToString("/")
                val parentNode = nodeMap.getValue(parentPath)
                (parentNode["children"] as MutableSet<String>).add(currentPath)
            }
        }

        val leafNode = nodeMap.getValue(currentPath)
        leafNode["probes_count"] = (item["probes_count"] as Number).toLong()
        leafNode["covered_probes"] = (item["aggregated_covered_probes"] as Number).toLong()
    }

    // Step 2: Collapse into a new map
    val collapsedNodeMap = mutableMapOf<String, MutableMap<String, Any?>>()

    fun collapseAndCopy(path: String, parentPath: String?): String {
        var node = nodeMap.getValue(path)
        var name = node["name"] as String
        var fullName = path
        var children = node["children"] as Set<String>

        while (children.size == 1) {
            val childPath = children.first()
            val child = nodeMap[childPath] ?: break

            // Only collapse consecutive single-child package nodes. Never collapse across the
            // class/method boundary (e.g. a class that contains only a single method), otherwise
            // the class node would be merged into its method and the method would dangle directly
            // under the package.
            if (child["type"] != TREEMAP_TYPE_PACKAGE) break

            val childName = child["name"] as String
            name = "$name/$childName"
            fullName = child["full_name"] as String
            node = child
            children = child["children"] as Set<String>
        }

        val nodeType = node["type"] as String
        val newNode = mutableMapOf<String, Any?>(
            "name" to name,
            "full_name" to fullName,
            "type" to nodeType,
            "parent" to parentPath,
            "probes_count" to node["probes_count"] as Long,
            "covered_probes" to node["covered_probes"] as Long,
            "params" to node["params"],
            "return_type" to node["return_type"],
            "children" to mutableSetOf<String>(),
            "package_name" to when (nodeType) {
                TREEMAP_TYPE_PACKAGE -> fullName
                else -> node["package_name"]
            },
            "class_name" to node["class_name"],
            "signature" to node["signature"],
        )
        collapsedNodeMap[fullName] = newNode

        for (child in children) {
            val newChildPath = collapseAndCopy(child, fullName)
            (newNode["children"] as MutableSet<String>).add(newChildPath)
        }

        return fullName
    }

    for ((path, node) in nodeMap) {
        if (node["parent"] == null) rootNodes.add(path)
    }

    val newRoots = mutableSetOf<String>()
    for (root in rootNodes) {
        val collapsedRoot = collapseAndCopy(root, null)
        newRoots.add(collapsedRoot)
    }

    // Step 3: Validate
    fun validateTreeStructure() {
        for ((path, node) in collapsedNodeMap) {
            val children = node["children"] as Set<String>
            for (child in children) {
                require(collapsedNodeMap.containsKey(child)) {
                    "Invalid tree: '$path' has non-existent child '$child'"
                }
                val childNode = collapsedNodeMap.getValue(child)
                require(childNode["parent"] == path) {
                    "Invalid tree: child's parent mismatch at '$child'"
                }
            }
        }

        for (item in data) {
            val pathParts = ("${item["class_name"]}/${item["method_name"]}").split("/")
            var currentPath = ""
            for ((index, part) in pathParts.withIndex()) {
                var nodePart = part
                if (index == pathParts.lastIndex) {
                    nodePart += "(${item["method_params"]}) -> ${item["return_type"]}"
                }
                currentPath = if (currentPath.isEmpty()) nodePart else "$currentPath/$nodePart"
            }
            require(collapsedNodeMap.containsKey(currentPath)) {
                "Missing node in collapsed tree: $currentPath"
            }
        }
    }

    validateTreeStructure()

    // Step 4: Aggregation
    fun computeAggregates(path: String) {
        val node = collapsedNodeMap.getValue(path)
        val children = node["children"] as MutableSet<String>
        for (child in children) {
            computeAggregates(child)
            val childNode = collapsedNodeMap.getValue(child)
            node["probes_count"] = (node["probes_count"] as Long) + (childNode["probes_count"] as Long)
            node["covered_probes"] = (node["covered_probes"] as Long) + (childNode["covered_probes"] as Long)
        }
    }

    for (root in newRoots) {
        computeAggregates(root)
    }

    fun serializeNode(path: String): Map<String, Any?> {
        val node = collapsedNodeMap.getValue(path)
        val children = (node["children"] as Set<String>)
            .map { serializeNode(it) }
            .sortedBy { it["name"] as String }

        return buildMap {
            put("name", node["name"])
            put("full_name", node["full_name"])
            put("type", node["type"])
            put("package_name", node["package_name"] as? String ?: "")
            put("probes_count", node["probes_count"])
            put("covered_probes", node["covered_probes"])
            node["class_name"]?.let { put("class_name", simpleClassName(it as String)) }
            node["signature"]?.let { put("signature", it) }
            node["params"]?.let { put("params", it) }
            node["return_type"]?.let { put("return_type", it) }
            put("children", children)
        }
    }

    return newRoots
        .map { serializeNode(it) }
        .sortedBy { it["name"] as String }
}
