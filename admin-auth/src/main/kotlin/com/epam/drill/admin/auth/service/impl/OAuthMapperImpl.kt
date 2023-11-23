package com.epam.drill.admin.auth.service.impl

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.epam.drill.admin.auth.config.OAuthConfig
import com.epam.drill.admin.auth.config.OAuthUnauthorizedException
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.service.OAuthMapper
import kotlinx.serialization.json.*

class OAuthMapperImpl(oauthConfig: OAuthConfig): OAuthMapper {
    private val tokenMapping = oauthConfig.tokenMapping
    private val userInfoMapping = oauthConfig.userInfoMapping
    private val roleMapping = oauthConfig.roleMapping

    override fun mergeUserEntities(userFromDatabase: UserEntity, userFromOAuth: UserEntity): UserEntity {
        return userFromDatabase.copy(
            role = if (Role.UNDEFINED.name != userFromOAuth.role) userFromOAuth.role else userFromDatabase.role
        )
    }

    override fun mapUserInfoToUserEntity(userInfoResponse: String): UserEntity {
        return Json.parseToJsonElement(userInfoResponse).run {
            UserEntity(
                username = getStringValue(userInfoMapping.username),
                role = mapRole(getStringArray(userInfoMapping.roles)).name
            )
        }
    }

    override fun mapAccessTokenToUserEntity(accessToken: String): UserEntity {
        return JWT.decode(accessToken).run {
            UserEntity(
                username = getStringValue(tokenMapping.username),
                role = mapRole(getStringArray(tokenMapping.roles)).name
            )
        }
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

private fun JsonElement.getStringValue(key: String): String =
    this.jsonObject[key]?.jsonPrimitive?.contentOrNull
        ?: throw OAuthUnauthorizedException("The key \"$key\" is not found in user-info response")

private fun JsonElement.getStringArray(key: String): List<String> =
    this.jsonObject[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

private fun DecodedJWT.getStringValue(key: String): String =
    getClaim(key).asString()
        ?: throw OAuthUnauthorizedException("The claim \"$key\" is not found in access token")

private fun DecodedJWT.getStringArray(key: String): List<String> =
    getClaim(key)?.takeIf { !it.isNull }?.asList(String::class.java) ?: emptyList()

