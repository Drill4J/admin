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
package com.epam.drill.admin.auth.entity

import java.time.LocalDateTime

data class ApiKeyEntity(
    val id: Int? = null,
    val userId: Int,
    val description: String,
    val apiKeyHash: String,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val user: UserEntity? = null
)