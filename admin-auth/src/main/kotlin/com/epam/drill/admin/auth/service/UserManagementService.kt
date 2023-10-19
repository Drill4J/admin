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

import com.epam.drill.admin.auth.view.CredentialsView
import com.epam.drill.admin.auth.view.UserPayload
import com.epam.drill.admin.auth.view.UserView

interface UserManagementService {
    fun getUsers(): List<UserView>

    fun getUser(userId: Int): UserView

    fun updateUser(userId: Int, payload: UserPayload): UserView

    fun deleteUser(userId: Int)

    fun blockUser(userId: Int)

    fun unblockUser(userId: Int)

    fun resetPassword(userId: Int): CredentialsView

}