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
package com.epam.drill.admin.version

import com.epam.drill.admin.*
import kotlinx.serialization.*

@Serializable
data class VersionDto(
    val admin: String,
    val java: String = "",
    val plugins: List<ComponentVersion> = emptyList(),
    val agents: List<ComponentVersion> = emptyList(),
)

@Serializable
data class ComponentVersion(
    val id: String,
    val version: String,
)

@Serializable
data class AdminVersionDto(
    val admin: String,
    val java: String = "",
)

val adminVersionDto = AdminVersionDto(
    admin = adminVersion,
    java = System.getProperty("java.version")
)
