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

internal fun buildTree(data: List<Map<String, Any?>>, rootId: String?): List<Map<String, Any?>> {
    val nodeMap = mutableMapOf<String, MutableMap<String, Any?>>()
    val rootNodes = mutableSetOf<String>()

    // Step 1: Build full uncollapsed tree
    for (item in data) {
        val pathParts = (item["name"] as String).split("/")
        var currentPath = ""

        for ((index, part) in pathParts.withIndex()) {
            var nodePart = part
            if (index == pathParts.lastIndex) {
                nodePart += "(${item["params"]}) -> ${item["return_type"]}"
            }

            currentPath = if (currentPath.isEmpty()) nodePart else "$currentPath/$nodePart"
            if (!rootId.isNullOrBlank() && !currentPath.startsWith(rootId)) {
                continue
            }
            if (!nodeMap.containsKey(currentPath)) {
                nodeMap[currentPath] = mutableMapOf(
                    "name" to nodePart,
                    "full_name" to currentPath,
                    "probes_count" to 0L,
                    "covered_probes" to 0L,
                    "children" to mutableSetOf<String>(),
                    "parent" to if (index == 0) null else pathParts.subList(0, index).joinToString("/"),
                    "params" to if (index == pathParts.lastIndex) item["params"] else null,
                    "return_type" to if (index == pathParts.lastIndex) item["return_type"] else null
                )
            }

            if (index > 0) {
                val parentPath = pathParts.subList(0, index).joinToString("/")
                val parentNode = nodeMap.getValue(parentPath)
                (parentNode["children"] as MutableSet<String>).add(currentPath)
            }
        }

        val leafNode = nodeMap.getValue(currentPath)
        leafNode["probes_count"] = item["probes_count"] as Long
        leafNode["covered_probes"] = item["covered_probes"] as Long
    }

    // Step 2: Collapse into a new map
    val collapsedNodeMap = mutableMapOf<String, MutableMap<String, Any?>>()

    fun collapseAndCopy(path: String, parentPath: String?): String {
        var node = nodeMap.getValue(path)
        var name = node["name"] as String
        var fullName = path
        var currentPath = path
        var children = node["children"] as Set<String>

        while (children.size == 1) {
            val childPath = children.first()
            val child = nodeMap[childPath] ?: break
            val grandChildren = child["children"] as Set<String>

            val isSecondToLast = grandChildren.any { (nodeMap[it]?.get("children") as? Set<*>)?.isEmpty() == true }
            if (isSecondToLast) break

            val childName = child["name"] as String
            name = "$name/$childName"
            fullName = child["full_name"] as String
            currentPath = fullName
            node = child
            children = grandChildren
        }

        val newNode = mutableMapOf(
            "name" to name,
            "full_name" to fullName,
            "parent" to parentPath,
            "probes_count" to node["probes_count"] as Long,
            "covered_probes" to node["covered_probes"] as Long,
            "params" to node["params"],
            "return_type" to node["return_type"],
            "children" to mutableSetOf<String>()
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
            val pathParts = (item["name"] as String).split("/")
            var currentPath = ""
            for ((index, part) in pathParts.withIndex()) {
                var nodePart = part
                if (index == pathParts.lastIndex) {
                    nodePart += "(${item["params"]}) -> ${item["return_type"]}"
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

    // Step 5: Flatten
    return collapsedNodeMap.values.map {
        mapOf(
            "name" to it["name"],
            "full_name" to it["full_name"],
            "parent" to it["parent"],
            "probes_count" to it["probes_count"],
            "covered_probes" to it["covered_probes"],
            "params" to it["params"],
            "return_type" to it["return_type"]
        )
    }
}
