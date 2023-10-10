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

import com.epam.drill.admin.users.exception.IncorrectCredentialsException
import com.epam.drill.admin.users.exception.IncorrectPasswordException
import com.epam.drill.admin.users.exception.UserAlreadyExistsException
import com.epam.drill.admin.users.exception.UserNotFoundException
import com.epam.drill.admin.users.repository.UserRepository
import com.epam.drill.admin.users.service.UserAuthenticationService
import com.epam.drill.admin.users.view.*
import io.ktor.auth.*

class UserAuthenticationServiceImpl(
    private val userRepository: UserRepository,
    private val passwordService: PasswordService
) : UserAuthenticationService {
    override fun signIn(form: LoginForm): TokenResponse {
        val entity = userRepository.findByUsername(form.username) ?: throw IncorrectCredentialsException()
        if (passwordService.checkPassword(form.password, entity.passwordHash))
            throw IncorrectCredentialsException()
        val token = issueToken(entity.toView())
        return TokenResponse(token)
    }

    override fun signUp(form: RegistrationForm) {
        if (userRepository.findByUsername(form.username) != null)
            throw UserAlreadyExistsException(form.username)
        passwordService.validatePassword(form.password)
        val passwordHash = passwordService.hashPassword(form.password)
        userRepository.create(form.toEntity(passwordHash))
    }

    override fun updatePassword(principal: UserIdPrincipal, form: ChangePasswordForm) {
        val entity = userRepository.findByUsername(principal.name) ?: throw UserNotFoundException()
        if (passwordService.checkPassword(form.oldPassword, entity.passwordHash))
            throw IncorrectPasswordException()
        passwordService.validatePassword(form.newPassword)
        entity.passwordHash = passwordService.hashPassword(form.newPassword)
        userRepository.update(entity)
    }

    private fun issueToken(user: UserView): String {
        TODO("Not yet implemented")
    }
}

