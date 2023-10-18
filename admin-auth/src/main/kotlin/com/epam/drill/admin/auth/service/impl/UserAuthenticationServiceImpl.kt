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
import com.epam.drill.admin.auth.exception.IncorrectCredentialsException
import com.epam.drill.admin.auth.exception.IncorrectPasswordException
import com.epam.drill.admin.auth.exception.UserAlreadyExistsException
import com.epam.drill.admin.auth.exception.UserNotFoundException
import com.epam.drill.admin.auth.entity.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.view.*
import io.ktor.auth.*

class UserAuthenticationServiceImpl(
    private val userRepository: UserRepository,
    private val passwordService: PasswordService
) : UserAuthenticationService {
    override fun signIn(form: LoginPayload): UserView {
        val entity = userRepository.findByUsername(form.username) ?: throw IncorrectCredentialsException()
        if (!passwordService.matchPasswords(form.password, entity.passwordHash))
            throw IncorrectCredentialsException()
        return entity.toView()
    }

    override fun signUp(form: RegistrationPayload) {
        if (userRepository.findByUsername(form.username) != null)
            throw UserAlreadyExistsException(form.username)
        passwordService.validatePasswordRequirements(form.password)
        val passwordHash = passwordService.hashPassword(form.password)
        userRepository.create(form.toEntity(passwordHash))
    }

    override fun updatePassword(principal: UserIdPrincipal, form: ChangePasswordPayload) {
        val entity = userRepository.findByUsername(principal.name) ?: throw UserNotFoundException()
        if (!passwordService.matchPasswords(form.oldPassword, entity.passwordHash))
            throw IncorrectPasswordException()
        passwordService.validatePasswordRequirements(form.newPassword)
        entity.passwordHash = passwordService.hashPassword(form.newPassword)
        userRepository.update(entity)
    }

}

private fun RegistrationPayload.toEntity(passwordHash: String): UserEntity {
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