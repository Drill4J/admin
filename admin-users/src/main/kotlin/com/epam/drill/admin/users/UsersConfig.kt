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
package com.epam.drill.admin.users

import com.epam.drill.admin.users.repository.UserRepository
import com.epam.drill.admin.users.repository.impl.UserRepositoryImpl
import com.epam.drill.admin.users.route.UserAuthenticationRoutes
import com.epam.drill.admin.users.route.UsersRoutes
import com.epam.drill.admin.users.service.UserAuthenticationService
import com.epam.drill.admin.users.service.UserManagementService
import com.epam.drill.admin.users.service.impl.UserAuthenticationServiceImpl
import com.epam.drill.admin.users.service.impl.UserManagementServiceImpl
import io.ktor.application.*
import org.kodein.di.*

val usersConfig: DI.Builder.(Application) -> Unit
    get() = { _ ->
        bind<UserAuthenticationRoutes>() with eagerSingleton { UserAuthenticationRoutes(di) }
        bind<UsersRoutes>() with eagerSingleton { UsersRoutes(di) }
        bind<UserAuthenticationService>() with eagerSingleton { UserAuthenticationServiceImpl(instance()) }
        bind<UserManagementService>() with eagerSingleton { UserManagementServiceImpl(instance()) }
        bind<UserRepository>() with eagerSingleton { UserRepositoryImpl() }
    }