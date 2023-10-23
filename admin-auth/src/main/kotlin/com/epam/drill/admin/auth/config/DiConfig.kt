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

import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.repository.impl.UserRepositoryImpl
import com.epam.drill.admin.auth.service.*
import com.epam.drill.admin.auth.service.impl.*
import com.epam.drill.admin.auth.service.transaction.TransactionalUserAuthenticationService
import com.epam.drill.admin.auth.service.transaction.TransactionalUserManagementService
import io.ktor.application.*
import org.kodein.di.*

val securityDiConfig: DI.Builder.(Application) -> Unit = { _ ->
    bindJwt()
    bind<SecurityConfig>() with eagerSingleton { SecurityConfig(di) }
    bind<PasswordRequirementsConfig>() with singleton { PasswordRequirementsConfig(di) }
}

val usersDiConfig: DI.Builder.(Application) -> Unit = { _ ->
    userRepositoriesConfig()
    userServicesConfig()
}

fun DI.Builder.bindJwt() {
    bind<JwtConfig>() with singleton { JwtConfig(di) }
    bind<JwtTokenService>() with singleton { JwtTokenService(instance()) }
    bind<TokenService>() with provider { instance<JwtTokenService>() }
}

fun DI.Builder.userServicesConfig() {
    bind<UserAuthenticationService>() with singleton {
        TransactionalUserAuthenticationService(
            UserAuthenticationServiceImpl(
                userRepository = instance(),
                passwordService = instance()
            )
        )
    }
    bind<UserManagementService>() with singleton {
        TransactionalUserManagementService(
            UserManagementServiceImpl(
                userRepository = instance(),
                passwordService = instance()
            )
        )
    }
    bind<PasswordGenerator>() with singleton { PasswordGeneratorImpl(config = instance()) }
    bind<PasswordValidator>() with singleton { PasswordValidatorImpl(config = instance()) }
    bind<PasswordService>() with singleton { PasswordServiceImpl(instance(), instance()) }
}

fun DI.Builder.userRepositoriesConfig() {
    bind<UserRepository>() with eagerSingleton {
        UserRepositoryImpl()
    }
}