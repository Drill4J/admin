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
import com.epam.drill.admin.auth.config.OAuth2Config
import com.epam.drill.admin.auth.config.OAuthUnauthorizedException
import com.epam.drill.admin.auth.config.UserMapping
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.service.OAuthMapper
import kotlinx.serialization.json.*

class OAuthMapperImpl(oauth2Config: OAuth2Config) : OAuthMapper {
    private val tokenMapping = oauth2Config.tokenMapping
    private val userInfoMapping = oauth2Config.userInfoMapping
    private val roleMapping = oauth2Config.roleMapping

    override fun updateDatabaseUserEntity(userFromDatabase: UserEntity, userFromOAuth: UserEntity): UserEntity {
        return userFromDatabase.copy(
            role = if (Role.UNDEFINED.name != userFromOAuth.role) userFromOAuth.role else userFromDatabase.role
        )
    }

    override fun mapUserInfoToUserEntity(userInfoResponse: String): UserEntity {
        return Json.parseToJsonElement(userInfoResponse).toUserEntity(userInfoMapping)
    }

    override fun mapAccessTokenPayloadToUserEntity(accessToken: String): UserEntity {
        return JWT.decode(accessToken).toUserEntity(tokenMapping)
    }

    private fun <T> T.toUserEntity(userMapping: UserMapping) = UserEntity(
        username = getStringValue(userMapping.username),
        role = getRole(userMapping.roles).name
    )

    private fun <T> T.getRole(rolesMapping: String?): Role {
        if (rolesMapping == null) return Role.UNDEFINED
        return mapRole(getStringArray(rolesMapping))
    }

    private fun mapRole(roleNames: List<String>?): Role {
        roleNames?.forEach {
            when (it.lowercase()) {
                roleMapping.user.lowercase() -> return Role.USER
                roleMapping.admin.lowercase() -> return Role.ADMIN
            }
        }
        return Role.UNDEFINED
    }
}

private fun <T> T.getStringValue(key: String) = when (this) {
    is JsonElement -> getStringValue(key)
    is DecodedJWT -> getStringValue(key)
    else -> throw OAuthUnauthorizedException("Unsupported source type ${this!!::class}")
}

private fun <T> T.getStringArray(key: String) = when (this) {
    is JsonElement -> getStringArray(key)
    is DecodedJWT -> getStringArray(key)
    else -> throw OAuthUnauthorizedException("Unsupported source type ${this!!::class}")
}


private fun JsonElement.getStringValue(key: String): String =
    this.jsonObject[key]?.jsonPrimitive?.contentOrNull
        ?: throw OAuthUnauthorizedException("The key \"$key\" is not found in user-info response $this")

private fun JsonElement.getStringArray(key: String): List<String> =
    this.jsonObject[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

private fun DecodedJWT.getStringValue(key: String): String =
    getClaim(key).asString()
        ?: throw OAuthUnauthorizedException("The claim \"$key\" is not found in access token payload ${this.payload}")

private fun DecodedJWT.getStringArray(key: String): List<String> =
    getClaim(key)?.takeIf { !it.isNull }?.asList(String::class.java) ?: emptyList()

