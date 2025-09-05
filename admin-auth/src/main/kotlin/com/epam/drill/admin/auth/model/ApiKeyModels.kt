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
package com.epam.drill.admin.auth.model

import com.epam.drill.admin.common.principal.Role
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class ApiKeyView(
    val id: Int,
    val userId: Int,
    val description: String,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val username: String,
    val role: Role,
    val userBlocked: Boolean
)

@Serializable
data class UserApiKeyView(
    val id: Int,
    val description: String,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
)

@Serializable
data class ApiKeyCredentialsView(
    val id: Int,
    val apiKey: String,
    val expiresAt: LocalDateTime
)

@Serializable
data class GenerateApiKeyPayload(
    val description: String,
    val expiryPeriod: ExpiryPeriod
)

enum class ExpiryPeriod(val months: Int) {
    ONE_MONTH(1),
    THREE_MONTHS(3),
    SIX_MONTHS(6),
    ONE_YEAR(12)
}