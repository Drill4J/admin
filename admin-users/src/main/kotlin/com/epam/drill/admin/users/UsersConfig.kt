package com.epam.drill.admin.users

import com.epam.drill.admin.users.repository.UserRepository
import com.epam.drill.admin.users.repository.impl.UserRepositoryImpl
import com.epam.drill.admin.users.route.ProfileRoutes
import com.epam.drill.admin.users.route.UsersRoutes
import com.epam.drill.admin.users.service.UserAuthenticationService
import com.epam.drill.admin.users.service.UserManagementService
import com.epam.drill.admin.users.service.impl.UserAuthenticationServiceImpl
import com.epam.drill.admin.users.service.impl.UserManagementServiceImpl
import io.ktor.application.*
import org.kodein.di.*

val usersConfig: DI.Builder.(Application) -> Unit
    get() = { _ ->
        bind<ProfileRoutes>() with eagerSingleton { ProfileRoutes(di) }
        bind<UsersRoutes>() with eagerSingleton { UsersRoutes(di) }
        bind<UserAuthenticationService>() with eagerSingleton { UserAuthenticationServiceImpl(instance()) }
        bind<UserManagementService>() with eagerSingleton { UserManagementServiceImpl(instance()) }
        bind<UserRepository>() with eagerSingleton { UserRepositoryImpl() }
    }