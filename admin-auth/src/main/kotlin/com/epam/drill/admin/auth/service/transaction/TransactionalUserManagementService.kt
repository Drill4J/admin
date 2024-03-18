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
package com.epam.drill.admin.auth.service.transaction

import com.epam.drill.admin.auth.model.CredentialsView
import com.epam.drill.admin.auth.model.EditUserPayload
import com.epam.drill.admin.auth.model.UserView
import com.epam.drill.admin.auth.service.UserManagementService
import com.epam.drill.admin.auth.config.AuthDatabaseConfig.transaction

class TransactionalUserManagementService(
    private val delegate: UserManagementService
): UserManagementService by delegate {
    override suspend fun getUsers(): List<UserView> = transaction {
        delegate.getUsers()
    }

    override suspend fun getUser(userId: Int): UserView = transaction {
        delegate.getUser(userId)
    }

    override suspend fun updateUser(userId: Int, payload: EditUserPayload): UserView = transaction {
        delegate.updateUser(userId, payload)
    }

    override suspend fun deleteUser(userId: Int) = transaction {
        delegate.deleteUser(userId)
    }

    override suspend fun blockUser(userId: Int) = transaction {
        delegate.blockUser(userId)
    }

    override suspend fun unblockUser(userId: Int) = transaction {
        delegate.unblockUser(userId)
    }

    override suspend fun resetPassword(userId: Int): CredentialsView = transaction {
        delegate.resetPassword(userId)
    }
}