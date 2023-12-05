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
package com.epam.drill.admin.auth.config

import io.ktor.application.*
import io.ktor.config.*
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

/**
 * A Ktor configuration for password strength settings based on the key "drill.auth.password".
 */
class PasswordStrengthConfig(override val di: DI) : DIAware {
    private val app by instance<Application>()
    private val jwt: ApplicationConfig
        get() = app.environment.config
            .config("drill")
            .config("auth")
            .config("password")

    /**
     * A minimum password length. Optional, 6 by default.
     */
    val minLength: Int
        get() = jwt.propertyOrNull("minLength")?.getString()?.toInt() ?: 6

    /**
     * Must the password contain uppercase. Optional, false by default.
     */
    val mustContainUppercase: Boolean
        get() = jwt.propertyOrNull("mustContainUppercase")?.getString()?.toBoolean() ?: false

    /**
     * Must the password contain lowercase. Optional, false by default.
     */
    val mustContainLowercase: Boolean
        get() = jwt.propertyOrNull("mustContainLowercase")?.getString()?.toBoolean() ?: false

    /**
     * Must the password contain digits. Optional, false by default.
     */
    val mustContainDigit: Boolean
        get() = jwt.propertyOrNull("mustContainDigit")?.getString()?.toBoolean() ?: false
}