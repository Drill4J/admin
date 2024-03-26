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
package com.epam.drill.admin.auth.repository

import com.epam.drill.admin.auth.entity.UserEntity


/**
 * A repository for storing users.
 */
interface UserRepository {
    /**
     * Finds all users.
     * @return the list of users
     */
    suspend fun findAll(): List<UserEntity>

    /**
     * Finds a user with the given id.
     * @param id the id of the user
     * @return the user or null if the user doesn't exist by given id
     */
    suspend fun findById(id: Int): UserEntity?

    /**
     * Finds a user with the given username.
     * @param username the username of the user
     * @return the user or null if the user doesn't exist by given username
     */
    suspend fun findByUsername(username: String): UserEntity?

    /**
     * Creates a new user.
     * @param entity the user to be created
     * @return the created user with filled id and registration date
     */
    suspend fun create(entity: UserEntity): UserEntity

    /**
     * Updates an existing user.
     * @param entity the user to be updated
     * @return the updated user
     */
    suspend fun update(entity: UserEntity): UserEntity

    /**
     * Deletes a user.
     * @param id the id of the user to be deleted
     */
    suspend fun deleteById(id: Int)
}