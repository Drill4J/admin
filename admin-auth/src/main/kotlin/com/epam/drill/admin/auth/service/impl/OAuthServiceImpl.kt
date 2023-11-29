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

import com.epam.drill.admin.auth.config.OAuthAccessDeniedException
import com.epam.drill.admin.auth.config.OAuthConfig
import com.epam.drill.admin.auth.config.OAuthUnauthorizedException
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.model.UserInfoView
import com.epam.drill.admin.auth.model.toUserInfoView
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.OAuthMapper
import com.epam.drill.admin.auth.service.OAuthService
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class OAuthServiceImpl(
    private val httpClient: HttpClient,
    private val oauthConfig: OAuthConfig,
    private val userRepository: UserRepository,
    private val oauthMapper: OAuthMapper
) : OAuthService {

    override suspend fun signInThroughOAuth(principal: OAuthAccessTokenResponse.OAuth2): UserInfoView {
        val oauthUser = oauthConfig.userInfoUrl
            ?.let { getUserInfo(it, principal.accessToken) }
            ?.let { oauthMapper.mapUserInfoToUserEntity(it) }
            ?: oauthMapper.mapAccessTokenPayloadToUserEntity(principal.accessToken)
        val dbUser = userRepository.findByUsername(oauthUser.username)
        if (dbUser?.blocked == true)
            throw OAuthAccessDeniedException()
        return createOrUpdateUser(dbUser, oauthUser).toUserInfoView()
    }

    private suspend fun createOrUpdateUser(
        dbUser: UserEntity?,
        oauthUser: UserEntity
    ): UserEntity = dbUser
        ?.let { oauthMapper.updateDatabaseUserEntity(dbUser, oauthUser) }
        ?.apply { userRepository.update(this) }
        ?: userRepository.create(oauthUser)

    private suspend fun getUserInfo(
        userInfoUrl: String,
        accessToken: String
    ): String = runCatching {
        httpClient
            .get<HttpResponse>(userInfoUrl) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }
            .receive<String>()
    }.onFailure { cause ->
        throw OAuthUnauthorizedException("User info request failed: ${cause.message}", cause)
    }.getOrThrow()
}