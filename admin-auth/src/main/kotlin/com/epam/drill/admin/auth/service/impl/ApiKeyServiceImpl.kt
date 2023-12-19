package com.epam.drill.admin.auth.service.impl

import com.epam.drill.admin.auth.entity.ApiKeyEntity
import com.epam.drill.admin.auth.exception.ApiKeyNotFoundException
import com.epam.drill.admin.auth.model.ApiKeyCredentialsView
import com.epam.drill.admin.auth.model.ApiKeyView
import com.epam.drill.admin.auth.model.GenerateApiKeyPayload
import com.epam.drill.admin.auth.model.UserApiKeyView
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

    override suspend fun getApiKeyById(id: Int): ApiKeyView {
        return repository.findById(id)?.toApiKeyView() ?: throw ApiKeyNotFoundException()
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
            id = entityWithId.id ?: throw IllegalStateException("Api key id cannot be null after creation"),
            apiKey = entityWithId.apiKeyHash,
            expiresAt = entityWithId.expiresAt.toKotlinLocalDateTime()
        )
    }

    private fun generateKey(): String {
        val random = SecureRandom()
        val keyBytes = ByteArray(32)
        random.nextBytes(keyBytes)
        return Base64.getEncoder().encodeToString(keyBytes)
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
