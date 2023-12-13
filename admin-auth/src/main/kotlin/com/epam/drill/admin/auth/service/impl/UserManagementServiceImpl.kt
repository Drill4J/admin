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
import com.epam.drill.admin.auth.exception.ForbiddenOperationException
import com.epam.drill.admin.auth.exception.UserNotFoundException
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.UserManagementService
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.model.CredentialsView
import com.epam.drill.admin.auth.model.EditUserPayload
import com.epam.drill.admin.auth.model.UserView
import com.epam.drill.admin.auth.service.PasswordGenerator
import kotlinx.datetime.toKotlinLocalDateTime

class UserManagementServiceImpl(
    private val userRepository: UserRepository,
    private val passwordService: PasswordService,
    private val passwordGenerator: PasswordGenerator,
    private val externalRoleManagement: Boolean = false
) : UserManagementService {
    override suspend fun getUsers(): List<UserView> {
        return userRepository.findAll().map { it.toApiKeyView() }
    }

    override suspend fun getUser(userId: Int): UserView {
        return findUser(userId).toApiKeyView()
    }

    override suspend fun updateUser(userId: Int, payload: EditUserPayload): UserView {
        val oldUserEntity = findUser(userId)
        if (externalRoleManagement && oldUserEntity.external)
            throw ForbiddenOperationException("Cannot update role for external user")
        val updatedUserEntity = userRepository.update(payload.toEntity(oldUserEntity))
        return updatedUserEntity.toApiKeyView()
    }

    override suspend fun deleteUser(userId: Int) {
        userRepository.deleteById(userId)
    }

    override suspend fun blockUser(userId: Int) {
        val userEntity = findUser(userId)
        userRepository.update(userEntity.copy(blocked = true))
    }

    override suspend fun unblockUser(userId: Int) {
        val userEntity = findUser(userId)
        userRepository.update(userEntity.copy(blocked = false))
    }

    override suspend fun resetPassword(userId: Int): CredentialsView {
        val userEntity = findUser(userId)
        if (userEntity.external)
            throw ForbiddenOperationException("Cannot reset password for external user")
        val newPassword = passwordGenerator.generatePassword()
        val newPasswordHash = passwordService.hashPassword(newPassword)
        userRepository.update(userEntity.copy(passwordHash = newPasswordHash))
        return userEntity.toCredentialsView(newPassword)
    }

    private suspend fun findUser(userId: Int) = userRepository.findById(userId) ?: throw UserNotFoundException()
}

private fun UserEntity.toCredentialsView(newPassword: String): CredentialsView {
    return CredentialsView(
        username = this.username,
        password = newPassword
    )
}

private fun UserEntity.toApiKeyView(): UserView {
    return UserView(
        id = this.id ?: throw NullPointerException("User id cannot be null"),
        username = this.username,
        role = Role.valueOf(this.role),
        blocked = this.blocked,
        registrationDate = this.registrationDate?.toKotlinLocalDateTime(),
        external = this.external
    )
}

private fun EditUserPayload.toEntity(oldUserEntity: UserEntity) = oldUserEntity.copy(
    role = this.role.name
)