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
import com.epam.drill.plugins.test2code.storage.*
import com.epam.dsm.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

@Serializable
internal data class GlobalAgentData(
    @Id val agentId: String,
    val baseline: Baseline = Baseline()
)

@Serializable
internal data class Baseline(
    val version: String = "",
    val parentVersion: String = ""
)

sealed class AgentData

internal object NoData : AgentData()

/**
 * Data for js or .Net agent
 */
internal class DataBuilder : AgentData(), Iterable<AstEntity> {

    private val _data = atomic(persistentListOf<AstEntity>())

    operator fun plusAssign(parts: Iterable<AstEntity>) = _data.update { it + parts }

    override fun iterator() = _data.value.iterator()
}

/**
 * Transformed data about packages, classes and methods of the agent
 * @param agentKey the pair of the agent ID and the build version
 * @param packageTree summary of packages, classes and methods
 * @param methods the list of all methods
 * @param probeIds the map where key is class name and value is a csr value of the class name
 */
@Serializable
data class ClassData(
    val agentKey: AgentKey,
    val packageTree: PackageTree = emptyPackageTree,
    @DeserializeWithPool val methods: List<Method> = emptyList(),
    val probeIds: Map<String, Long> = emptyMap()
) : AgentData() {
    companion object {
        private val emptyPackageTree = PackageTree()
    }

    override fun equals(other: Any?) = other is ClassData && agentKey == other.agentKey

    override fun hashCode() = agentKey.hashCode()
}

/**
 * Convert the tree of application packages to a class data instance
 */
fun PackageTree.toClassData(
    agentKey: AgentKey,
    methods: List<Method>,
    probeIds: Map<String, Long> = emptyMap()
) = ClassData(
    agentKey = agentKey,
    packageTree = this,
    methods = methods,
    probeIds = probeIds
)
