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
import com.epam.drill.admin.auth.repository.impl.EnvUserRepository
import com.epam.drill.admin.auth.service.PasswordGenerator
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.service.UserManagementService
import com.epam.drill.admin.auth.service.impl.PasswordGeneratorImpl
import com.epam.drill.admin.auth.service.impl.PasswordServiceImpl
import com.epam.drill.admin.auth.service.impl.UserAuthenticationServiceImpl
import com.epam.drill.admin.auth.service.impl.UserManagementServiceImpl
import io.ktor.application.*
import org.kodein.di.*

val usersDiConfig: DI.Builder.(Application) -> Unit = { _ ->
    userRepositoriesConfig()
    userServicesConfig()
}

fun DI.Builder.userServicesConfig() {
    bind<UserAuthenticationService>() with eagerSingleton {
        UserAuthenticationServiceImpl(
            instance(),
            instance()
        )
    }
    bind<UserManagementService>() with eagerSingleton { UserManagementServiceImpl(instance(), instance()) }
    bind<PasswordGenerator>() with singleton { PasswordGeneratorImpl() }
    bind<PasswordService>() with singleton { PasswordServiceImpl(instance()) }
}

fun DI.Builder.userRepositoriesConfig() {
    bind<UserRepository>() with eagerSingleton {
        EnvUserRepository(
            env = instance<Application>().environment.config,
            passwordService = instance()
        )
    }
}