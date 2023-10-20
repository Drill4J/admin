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
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.exception.*
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.model.*
import io.ktor.auth.*

class UserAuthenticationServiceImpl(
    private val userRepository: UserRepository,
    private val passwordService: PasswordService
) : UserAuthenticationService {
    override fun signIn(payload: LoginPayload): UserView {
        val userEntity = userRepository.findByUsername(payload.username) ?: throw IncorrectCredentialsException()
        if (!passwordService.matchPasswords(payload.password, userEntity.passwordHash))
            throw IncorrectCredentialsException()
        return userEntity.toView()
    }

    override fun signUp(payload: RegistrationPayload) {
        if (userRepository.findByUsername(payload.username) != null)
            throw UserValidationException("User '${payload.username}' already exists")
        passwordService.validatePasswordRequirements(payload.password)
        val passwordHash = passwordService.hashPassword(payload.password)
        userRepository.create(payload.toUserEntity(passwordHash))
    }

    override fun updatePassword(principal: UserIdPrincipal, payload: ChangePasswordPayload) {
        val userEntity = userRepository.findByUsername(principal.name) ?: throw UserNotFoundException()
        if (!passwordService.matchPasswords(payload.oldPassword, userEntity.passwordHash))
            throw UserValidationException("Old password is incorrect")
        passwordService.validatePasswordRequirements(payload.newPassword)
        userEntity.passwordHash = passwordService.hashPassword(payload.newPassword)
        userRepository.update(userEntity)
    }

}

private fun RegistrationPayload.toUserEntity(passwordHash: String): UserEntity {
    return UserEntity(
        username = this.username,
        passwordHash = passwordHash,
        role = Role.UNDEFINED.name,
    )
}

private fun UserEntity.toView(): UserView {
    return UserView(
        username = this.username,
        role = Role.valueOf(this.role),
        blocked = this.blocked
    )
}