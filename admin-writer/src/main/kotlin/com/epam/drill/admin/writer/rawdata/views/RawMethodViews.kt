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
package com.epam.drill.admin.writer.rawdata.views

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val RAW_METHOD_TREE_TYPE_PACKAGE = "package"
const val RAW_METHOD_TREE_TYPE_CLASS = "class"
const val RAW_METHOD_TREE_TYPE_METHOD = "method"

@Serializable
data class RawMethodTreeNodeView(
    val type: String,
    val name: String,
    @SerialName("full_name")
    val fullName: String,
    @SerialName("package_name")
    val packageName: String,
    @SerialName("class_name")
    val className: String? = null,
    @SerialName("method_id")
    val methodId: String? = null,
    @SerialName("method_name")
    val methodName: String? = null,
    val signature: String? = null,
    val params: String? = null,
    @SerialName("return_type")
    val returnType: String? = null,
    /** Layout size for treemap (at least 1 so zero-probe methods remain visible). */
    @SerialName("probes_count")
    val probesCount: Long = 0,
    /** Unused for coverage coloring; kept for CoverageTreemapCanvas layout compatibility. */
    @SerialName("covered_probes")
    val coveredProbes: Long = 0,
    val ignored: Boolean = false,
    @SerialName("total_methods")
    val totalMethods: Int = 0,
    @SerialName("ignored_methods")
    val ignoredMethods: Int = 0,
    val children: List<RawMethodTreeNodeView> = emptyList(),
)

@Serializable
data class RawMethodTreeView(
    val roots: List<RawMethodTreeNodeView>,
    /** Methods matched by saved ignore rules. */
    val affectedMethods: Long,
    /** Total methods in the build catalog under this tree. */
    val totalMethods: Long,
)

@Serializable
data class RawMethodView(
    val methodId: String,
    val methodName: String,
    val className: String,
    val signature: String,
    val methodParams: String,
    val returnType: String,
    val probesCount: Int,
    val ignored: Boolean,
    val matchingRuleIds: List<Int>,
)

@Serializable
data class RawMethodPageView(
    val data: List<RawMethodView>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
)

data class RawMethodLeaf(
    val methodId: String,
    val methodName: String,
    val className: String,
    val packageName: String,
    val signature: String,
    val methodParams: String,
    val returnType: String,
    val probesCount: Int,
    val ignored: Boolean,
)
