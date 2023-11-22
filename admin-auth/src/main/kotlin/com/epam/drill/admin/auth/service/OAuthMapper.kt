package com.epam.drill.admin.auth.service

import com.epam.drill.admin.auth.entity.UserEntity

interface OAuthMapper {
    fun mergeUserEntities(userFromDatabase: UserEntity, userFromOAuth: UserEntity): UserEntity

    fun mapUserInfoToUserEntity(userInfoResponse: String): UserEntity

    fun mapAccessTokenToUserEntity(accessToken: String): UserEntity
}