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
package com.epam.drill.admin.users.service.impl

import com.epam.drill.admin.users.repository.UserRepository
import com.epam.drill.admin.users.service.UserManagementService
import com.epam.drill.admin.users.view.CredentialsView
import com.epam.drill.admin.users.view.UserForm
import com.epam.drill.admin.users.view.UserView

class UserManagementServiceImpl(val userRepository: UserRepository): UserManagementService {
    override fun getUsers(): List<UserView> {
        TODO("Not yet implemented")
    }

    override fun getUser(userId: Int) {
        TODO("Not yet implemented")
    }

    override fun updateUser(userId: Int, form: UserForm) {
        TODO("Not yet implemented")
    }

    override fun deleteUser(userId: Int) {
        TODO("Not yet implemented")
    }

    override fun blockUser(userId: Int) {
        TODO("Not yet implemented")
    }

    override fun unblockUser(userId: Int) {
        TODO("Not yet implemented")
    }

    override fun resetPassword(userId: Int): CredentialsView {
        TODO("Not yet implemented")
    }
}