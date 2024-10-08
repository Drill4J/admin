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

/**
 * A service for working with passwords.
 */
interface PasswordService {
    /**
     * Hashes the given password.
     * @param password the password to be hashed
     * @return the hashed password
     */
    fun hashPassword(password: String): String

    /**
     * Compares the given non hashed password with the hashed one.
     * @param candidate the non hashed password
     * @param hashed the hashed password to compare
     * @return true if the passwords match
     */
    fun matchPasswords(candidate: String, hashed: String?): Boolean
}
