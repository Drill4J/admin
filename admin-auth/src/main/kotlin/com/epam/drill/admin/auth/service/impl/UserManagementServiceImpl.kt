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

import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.exception.UserNotFoundException
import com.epam.drill.admin.auth.entity.Role
import com.epam.drill.admin.auth.model.UserStatus
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.UserManagementService
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.view.CredentialsView
import com.epam.drill.admin.auth.view.UserPayload
import com.epam.drill.admin.auth.view.UserView

class UserManagementServiceImpl(
    private val userRepository: UserRepository,
    private val passwordService: PasswordService
) : UserManagementService {
    override fun getUsers(): List<UserView> {
        return userRepository.findAllNotDeleted().map { it.toView() }
    }

    override fun getUser(userId: Int): UserView {
        return userRepository.findById(userId)?.toView() ?: throw UserNotFoundException()
    }

    override fun updateUser(userId: Int, form: UserPayload): UserView {
        val entity = userRepository.findById(userId) ?: throw UserNotFoundException()
        entity.role = form.role.name
        userRepository.update(entity)
        return entity.toView()
    }

    override fun deleteUser(userId: Int) {
        val entity = userRepository.findById(userId) ?: throw UserNotFoundException()
        entity.deleted = true
        userRepository.update(entity)
    }

    override fun blockUser(userId: Int) {
        val entity = userRepository.findById(userId) ?: throw UserNotFoundException()
        entity.blocked = true
        userRepository.update(entity)
    }

    override fun unblockUser(userId: Int) {
        val entity = userRepository.findById(userId) ?: throw UserNotFoundException()
        entity.blocked = false
        userRepository.update(entity)
    }

    override fun resetPassword(userId: Int): CredentialsView {
        val entity = userRepository.findById(userId) ?: throw UserNotFoundException()
        val newPassword = passwordService.generatePassword()
        entity.passwordHash = passwordService.hashPassword(newPassword)
        userRepository.update(entity)
        return entity.toCredentialsView(newPassword)
    }
}

private fun UserEntity.toCredentialsView(newPassword: String): CredentialsView {
    return CredentialsView(
        username = this.username,
        password = newPassword,
        role = Role.valueOf(this.role)
    )
}

private fun UserEntity.toView(): UserView {
    return UserView(
        username = this.username,
        role = Role.valueOf(this.role),
        blocked = this.blocked
    )
}
