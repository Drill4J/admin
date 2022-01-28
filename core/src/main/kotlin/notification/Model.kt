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
package com.epam.drill.admin.notification

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class Notification(
    val id: String,
    val agentId: String,
    val createdAt: Long,
    val type: NotificationType,
    val read: Boolean = false,
    val message: JsonElement,
)

enum class NotificationType {
    BUILD
}

@Serializable
data class NewBuildArrivedMessage(
    val currentId: String,
    val recommendations: Set<String> = emptySet(),
    val buildInfo: JsonElement = JsonNull,
)
