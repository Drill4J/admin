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
package com.epam.drill.admin.auth.service.impl

import com.epam.drill.admin.auth.entity.ApiKeyEntity
import com.epam.drill.admin.auth.exception.ApiKeyNotFoundException
import com.epam.drill.admin.auth.model.ApiKeyCredentialsView
import com.epam.drill.admin.auth.model.ApiKeyView
import com.epam.drill.admin.auth.model.GenerateApiKeyPayload
import com.epam.drill.admin.auth.model.UserApiKeyView
import com.epam.drill.admin.auth.exception.NotAuthenticatedException
import com.epam.drill.admin.auth.exception.NotAuthorizedException
import com.epam.drill.admin.auth.model.*
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.ApiKeyRepository
import com.epam.drill.admin.auth.service.*
import kotlinx.datetime.toKotlinLocalDateTime
import java.security.SecureRandom
import java.time.LocalDateTime

class ApiKeyServiceImpl(
    private val repository: ApiKeyRepository,
    private val passwordService: PasswordService,
    private val apiKeyBuilder: ApiKeyBuilder,
    private val secretGenerator: SecretGenerator,
    private val currentDateTimeProvider: () -> LocalDateTime = { LocalDateTime.now() },
): ApiKeyService {
    override suspend fun getAllApiKeys(): List<ApiKeyView> {
        return repository.findAll()
            .map { it.toApiKeyView() }
    }

    override suspend fun getApiKeysByUser(userId: Int): List<UserApiKeyView> {
        return repository.findAllByUserId(userId)
            .map { it.toUserApiKeyView() }
    }

    override suspend fun getApiKeyById(id: Int): ApiKeyView {
        return repository.findById(id)?.toApiKeyView() ?: throw ApiKeyNotFoundException()
    }

    override suspend fun deleteApiKey(id: Int) {
        repository.deleteById(id)
    }

    override suspend fun generateApiKey(userId: Int, payload: GenerateApiKeyPayload): ApiKeyCredentialsView {
        val secret = secretGenerator.generate()
        val hash = passwordService.hashPassword(secret)
        val entity = ApiKeyEntity(
            userId = userId,
            description = payload.description,
            apiKeyHash = hash,
            expiresAt = currentDateTimeProvider().plusMonths(payload.expiryPeriod.months.toLong()),
            createdAt = currentDateTimeProvider()
        )
        val entityWithId = repository.create(entity)
        val apiKeyId: Int = entityWithId.id ?: throw IllegalStateException("Api key id cannot be null after creation")
        val apiKey = apiKeyBuilder.format(ApiKey(apiKeyId, secret))
        return ApiKeyCredentialsView(
            id = entityWithId.id,
            apiKey = apiKey,
            expiresAt = entityWithId.expiresAt.toKotlinLocalDateTime()
        )
    }

    override suspend fun signInThroughApiKey(apiKey: String): UserInfoView {
        val (apiKeyId, secret) = apiKeyBuilder.parse(apiKey)
        val entity = repository.findById(apiKeyId) ?: throw NotAuthenticatedException("Api key was not found")

        if (!passwordService.matchPasswords(secret, entity.apiKeyHash))
            throw NotAuthenticatedException("Api key is incorrect")

        if (entity.expiresAt < currentDateTimeProvider())
            throw NotAuthenticatedException("Api key has expired")

        if (entity.user?.blocked == true || Role.UNDEFINED.name == entity.user?.role)
            throw NotAuthorizedException()

        return entity.user?.toUserInfoView() ?: throw NullPointerException("Api key user cannot be null")
    }
}

private fun ApiKeyEntity.toApiKeyView() = ApiKeyView(
    id = id ?: throw IllegalStateException("Api Key id cannot be null"),
    userId = userId,
    description = description,
    expiresAt = expiresAt.toKotlinLocalDateTime(),
    createdAt = createdAt.toKotlinLocalDateTime(),
    username = user?.username ?: throw IllegalStateException("User property cannot be null in ApiKeyEntity"),
    role = user.role.let { Role.valueOf(it) },
)

private fun ApiKeyEntity.toUserApiKeyView() = UserApiKeyView(
    id = id ?: throw IllegalStateException("Api Key id cannot be null"),
    description = description,
    expiresAt = expiresAt.toKotlinLocalDateTime(),
    createdAt = createdAt.toKotlinLocalDateTime(),
)