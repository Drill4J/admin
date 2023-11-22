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
        ?: Role.UNDEFINED.name
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
    role = if (Role.UNDEFINED.name != other.role) other.role else this.role
)

private fun UserEntity.toView(): UserInfoView {
    return UserInfoView(
        username = this.username,
        role = Role.valueOf(this.role)
    )
}