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

import com.epam.drill.admin.auth.model.CredentialsView
import com.epam.drill.admin.auth.model.EditUserPayload
import com.epam.drill.admin.auth.model.UserView

/**
 * A service for working with users.
 */
interface UserManagementService {
    /**
     * Returns a list of all users.
     * @return the list of users
     */
    suspend fun getUsers(): List<UserView>

    /**
     * Returns a user with the given id.
     * @param userId the id of the user to be returned
     * @return the user
     */
    suspend fun getUser(userId: Int): UserView

    /**
     * Updates a user.
     * @param userId the id of the user to be updated
     * @param payload the user data to be updated
     * @return the updated user
     * @exception UserValidationException if the user data is not valid
     */
    suspend fun updateUser(userId: Int, payload: EditUserPayload): UserView

    /**
     * Deletes a user.
     * @param userId the id of the user to be deleted
     */
    suspend fun deleteUser(userId: Int)

    /**
     * Blocks a user.
     * @param userId the id of the user to be blocked
     */
    suspend fun blockUser(userId: Int)

    /**
     * Unblocks a user.
     * @param userId the id of the user to be unblocked
     */
    suspend fun unblockUser(userId: Int)

    /**
     * Resets a user's password.
     * @param userId the id of the user whose password should be reset
     * @return the new credentials of the user
     */
    suspend fun resetPassword(userId: Int): CredentialsView

}