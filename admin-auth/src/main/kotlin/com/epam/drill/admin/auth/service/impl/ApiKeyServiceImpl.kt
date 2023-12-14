package com.epam.drill.admin.auth.service.impl

import com.epam.drill.admin.auth.entity.ApiKeyEntity
import com.epam.drill.admin.auth.exception.NotAuthenticatedException
import com.epam.drill.admin.auth.exception.NotAuthorizedException
import com.epam.drill.admin.auth.model.*
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.ApiKeyRepository
import com.epam.drill.admin.auth.service.ApiKeyService
import com.epam.drill.admin.auth.service.PasswordService
import kotlinx.datetime.toKotlinLocalDateTime
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.*

class ApiKeyServiceImpl(
    private val repository: ApiKeyRepository,
    private val passwordService: PasswordService,
    private val currentDateTimeProvider: () -> LocalDateTime = { LocalDateTime.now() }
): ApiKeyService {
    override suspend fun getAllApiKeys(): List<ApiKeyView> {
        return repository.getAll()
            .map { it.toApiKeyView() }
    }

    override suspend fun getApiKeysByUser(userId: Int): List<UserApiKeyView> {
        return repository.getAllByUserId(userId)
            .map { it.toUserApiKeyView() }
    }

    override suspend fun deleteApiKey(id: Int) {
        repository.deleteById(id)
    }

    override suspend fun generateApiKey(userId: Int, payload: GenerateApiKeyPayload): ApiKeyCredentialsView {
        val apiKey = generateKey()
        val apiKeyHash = passwordService.hashPassword(apiKey)
        val entity = ApiKeyEntity(
            userId = userId,
            description = payload.description,
            apiKeyHash = apiKeyHash,
            expiresAt = currentDateTimeProvider().plusMonths(payload.expiryPeriod.months.toLong()),
            createdAt = currentDateTimeProvider()
        )
        val entityWithId = repository.create(entity)
        return ApiKeyCredentialsView(
            id = entityWithId.id ?: throw NullPointerException("Api key id cannot be null after creation"),
            apiKey = apiKey,
            expiresAt = entityWithId.expiresAt.toKotlinLocalDateTime()
        )
    }

    override suspend fun signInThroughApiKey(apiKey: String): UserInfoView {
        val apiKeyEntity = repository.getAll().find { entity ->
            passwordService.matchPasswords(apiKey, entity.apiKeyHash)
        } ?: throw NotAuthenticatedException("Api key is incorrect")

        if (apiKeyEntity.expiresAt < currentDateTimeProvider())
            throw NotAuthenticatedException("Api key expired")

        if (apiKeyEntity.user?.blocked == true || Role.UNDEFINED.name == apiKeyEntity.user?.role)
            throw NotAuthorizedException()

        return apiKeyEntity.user?.toUserInfoView() ?: throw NullPointerException("Api key user cannot be null")
    }

    private fun generateKey(): String {
        val random = SecureRandom()
        val keyBytes = ByteArray(32)
        random.nextBytes(keyBytes)
        return Base64.getEncoder().encodeToString(keyBytes)
    }
}

private fun ApiKeyEntity.toApiKeyView() = ApiKeyView(
    id = id ?: throw NullPointerException("Api Key id cannot be null"),
    userId = userId,
    description = description,
    expiresAt = expiresAt.toKotlinLocalDateTime(),
    createdAt = createdAt.toKotlinLocalDateTime(),
    username = user?.username ?: throw NullPointerException("User property cannot be null in ApiKeyEntity"),
    role = user.role.let { Role.valueOf(it) },
)

private fun ApiKeyEntity.toUserApiKeyView() = UserApiKeyView(
    id = id ?: throw NullPointerException("Api Key id cannot be null"),
    description = description,
    expiresAt = expiresAt.toKotlinLocalDateTime(),
    createdAt = createdAt.toKotlinLocalDateTime(),
)
