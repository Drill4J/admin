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

import com.epam.drill.admin.auth.service.PasswordGenerator
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.service.PasswordValidator
import org.mindrot.jbcrypt.BCrypt

class PasswordServiceImpl(
    private val passwordGenerator: PasswordGenerator,
    private val passwordValidator: PasswordValidator
) : PasswordService {
    override fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    override fun matchPasswords(candidate: String, hashed: String?): Boolean {
        if (hashed == null) return false
        return BCrypt.checkpw(candidate, hashed)
    }

    override fun generatePassword(): String {
        return passwordGenerator.generatePassword()
    }

    override fun validatePasswordRequirements(password: String) {
        passwordValidator.validatePasswordRequirements(password)
    }
}