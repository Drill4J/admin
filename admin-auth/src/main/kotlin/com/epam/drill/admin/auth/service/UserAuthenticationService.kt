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

import com.epam.drill.admin.auth.exception.UserValidationException
import com.epam.drill.admin.auth.model.*
import com.epam.drill.admin.auth.principal.User

/**
 * A service for authentication and user profile management.
 */
interface UserAuthenticationService {
    /**
     * Authenticates a user.
     * @param payload the username and password to be authenticated
     * @return the user information
     * @exception NotAuthenticatedException if the username or password is incorrect
     */
    suspend fun signIn(payload: LoginPayload): UserInfoView

    /**
     * Registers a new user.
     * @param payload the user information to be registered
     * @exception UserValidationException if the user already exists
     */
    suspend fun signUp(payload: RegistrationPayload)

    /**
     * Returns the user information by a current user principal object.
     * @param principal the current user principal object
     * @return the information of the current user
     */
    suspend fun getUserInfo(principal: User): UserInfoView

    /**
     * Updates the user password.
     * @param principal the current user principal object
     * @param payload the old password and the new one to be updated
     * @exception UserValidationException if the old password is incorrect
     */
    suspend fun updatePassword(principal: User, payload: ChangePasswordPayload)
}