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
package com.epam.drill.admin.auth.service

import com.epam.drill.admin.auth.entity.UserEntity

/**
 * A service for mapping OAuth2 access token and user info to database user entities.
 */
interface OAuthMapper {
    /**
     * Updates the database user entity based on the OAuth user entity.
     * @param userFromDatabase the user entity returned from the database
     * @param userFromOAuth the user entity returned from the OAuth provider
     * @return the updated user entity
     */
    fun updateDatabaseUserEntity(userFromDatabase: UserEntity, userFromOAuth: UserEntity): UserEntity

    /**
     * Maps OAuth2 user info response to user entity.
     * @param userInfoResponse the user info response
     * @return the user entity
     */
    fun mapUserInfoToUserEntity(userInfoResponse: String): UserEntity

    /**
     * Maps OAuth2 access token payload to user entity.
     * @param accessToken the access token
     * @return the user entity
     */
    fun mapAccessTokenPayloadToUserEntity(accessToken: String): UserEntity
}