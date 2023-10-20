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
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.UserManagementService
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.model.CredentialsView
import com.epam.drill.admin.auth.model.EditUserPayload
import com.epam.drill.admin.auth.model.UserView

class UserManagementServiceImpl(
    private val userRepository: UserRepository,
    private val passwordService: PasswordService
) : UserManagementService {
    override fun getUsers(): List<UserView> {
        return userRepository.findAllNotDeleted().map { it.toView() }
    }

    override fun getUser(userId: Int): UserView {
        return findUser(userId).toView()
    }

    override fun updateUser(userId: Int, payload: EditUserPayload): UserView {
        val userEntity = findUser(userId)
        userEntity.role = payload.role.name
        userRepository.update(userEntity)
        return userEntity.toView()
    }

    override fun deleteUser(userId: Int) {
        val userEntity = findUser(userId)
        userEntity.deleted = true
        userRepository.update(userEntity)
    }

    override fun blockUser(userId: Int) {
        val userEntity = findUser(userId)
        userEntity.blocked = true
        userRepository.update(userEntity)
    }

    override fun unblockUser(userId: Int) {
        val userEntity = findUser(userId)
        userEntity.blocked = false
        userRepository.update(userEntity)
    }

    override fun resetPassword(userId: Int): CredentialsView {
        val userEntity = findUser(userId)
        val newPassword = passwordService.generatePassword()
        userEntity.passwordHash = passwordService.hashPassword(newPassword)
        userRepository.update(userEntity)
        return userEntity.toCredentialsView(newPassword)
    }

    private fun findUser(userId: Int) = userRepository.findById(userId) ?: throw UserNotFoundException()
}

private fun UserEntity.toCredentialsView(newPassword: String): CredentialsView {
    return CredentialsView(
        username = this.username,
        password = newPassword
    )
}

private fun UserEntity.toView(): UserView {
    return UserView(
        username = this.username,
        role = Role.valueOf(this.role),
        blocked = this.blocked
    )
}
