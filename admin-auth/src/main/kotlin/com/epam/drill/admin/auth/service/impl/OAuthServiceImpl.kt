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

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.epam.drill.admin.auth.config.OAuthConfig
import com.epam.drill.admin.auth.config.OAuthUnauthorizedException
import com.epam.drill.admin.auth.config.RoleMapping
import com.epam.drill.admin.auth.config.UserMapping
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
        val oauthUser = oauthConfig.userInfoUrl
            ?.let {
                getUserInfo(it, principal.accessToken)
            }?.toUserEntity(oauthConfig.userInfoMapping, oauthConfig.roleMapping)
            ?: JWT.decode(principal.accessToken)
                .toUserEntity(oauthConfig.tokenMapping, oauthConfig.roleMapping)
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
        userInfoUrl: String,
        accessToken: String
    ): JsonElement = runCatching {
        httpClient
            .get<HttpResponse>(userInfoUrl) {
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


private fun UserEntity.merge(other: UserEntity) = copy(
    role = other.role ?: this.role
)

private fun UserEntity.toView() = UserInfoView(
    username = this.username,
    role = Role.valueOf(this.role ?: Role.UNDEFINED.name)
)

private fun JsonElement.toUserEntity(userMapping: UserMapping, roleMapping: RoleMapping) = UserEntity(
    username = findStringValue(userMapping.username)
        ?: throw OAuthUnauthorizedException("The key \"${userMapping.username}\" is not found in userinfo response"),
    role = findRole(
        findStringArray(userMapping.roles),
        roleMapping
    )?.name
)

private fun DecodedJWT.toUserEntity(userMapping: UserMapping, roleMapping: RoleMapping) = UserEntity(
    username = getClaim(userMapping.username).asString()
        ?: throw OAuthUnauthorizedException("The claim \"${userMapping.username}\" is not found in access token"),
    role = findRole(
        getClaim(userMapping.roles).asList(String::class.java),
        roleMapping
    )?.name
)

private fun findRole(roleNames: List<String>?, roleMapping: RoleMapping): Role? {
    roleNames?.forEach {
        when (it.lowercase()) {
            roleMapping.user.lowercase() -> return Role.USER
            roleMapping.admin.lowercase() -> return Role.ADMIN
        }
    }
    return null
}

private fun JsonElement.findStringValue(key: String) =
    this.jsonObject[key]?.jsonPrimitive?.contentOrNull

private fun JsonElement.findStringArray(key: String) =
    this.jsonObject[key]?.jsonArray?.map { it.jsonPrimitive.content }