package com.epam.drill.admin.auth.service.impl

import com.epam.drill.admin.auth.config.OAuthConfig
import com.epam.drill.admin.auth.config.OAuthUnauthorizedException
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.model.UserInfoView
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.OAuthService
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class OAuthServiceImpl(
    private val httpClient: HttpClient,
    private val oauthConfig: OAuthConfig,
    private val userRepository: UserRepository
) : OAuthService {

    override suspend fun signInThroughOAuth(principal: OAuthAccessTokenResponse.OAuth2): UserInfoView {
        val oauthUser = getUserInfo(principal.accessToken).toEntity()
        val dbUser = userRepository.findByUsername(oauthUser.username)
        if (dbUser?.blocked == true)
            throw OAuthUnauthorizedException("User is blocked")
        return createOrUpdateUser(oauthUser, dbUser).toView()
    }

    private suspend fun createOrUpdateUser(
        oauthUser: UserEntity,
        dbUser: UserEntity?
    ): UserEntity = dbUser
        ?.merge(oauthUser)
        ?.apply { userRepository.update(this) }
        ?: oauthUser
            .run { userRepository.create(this) }
            .let { oauthUser.copy(id = it) }

    private suspend fun getUserInfo(
        accessToken: String
    ): JsonElement = runCatching {
        httpClient
            .get<HttpResponse>(oauthConfig.userInfoUrl) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }
            .receive<String>()
            .let { Json.parseToJsonElement(it) }
    }.onFailure { cause ->
        throw OAuthUnauthorizedException("User info request failed: ${cause.message}", cause)
    }.getOrThrow()
}

private fun JsonElement.toEntity(): UserEntity = UserEntity(
    username = jsonObject.getValue("preferred_username").jsonPrimitive.content,
    role = jsonObject["roles"]
        ?.jsonArray
        ?.map { it.jsonPrimitive.content }
        .let { findRole(it)?.name }
)

private fun findRole(roleNames: List<String>?): Role? = roleNames
    ?.takeIf { it.isNotEmpty() }
    ?.distinct()
    ?.map { it.lowercase() }
    ?.let { roleNamesList ->
        Role.values().find { role ->
            roleNamesList.contains(role.name.lowercase())
        }
    }

private fun UserEntity.merge(other: UserEntity) = copy(
    role = other.role ?: this.role
)

private fun UserEntity.toView(): UserInfoView {
    return UserInfoView(
        username = this.username,
        role = Role.valueOf(this.role ?: Role.UNDEFINED.name)
    )
}