package com.epam.drill.admin.users

import com.epam.drill.admin.users.repository.UserRepository
import com.epam.drill.admin.users.repository.impl.UserRepositoryImpl
import com.epam.drill.admin.users.route.UserAuthenticationRoutes
import com.epam.drill.admin.users.route.UsersRoutes
import com.epam.drill.admin.users.service.UserAuthenticationService
import com.epam.drill.admin.users.service.UserManagementService
import com.epam.drill.admin.users.service.impl.PasswordService
import com.epam.drill.admin.users.service.impl.UserAuthenticationServiceImpl
import com.epam.drill.admin.users.service.impl.UserManagementServiceImpl
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.serialization.*
import org.kodein.di.*

val testApp: Application.() -> Unit = {
    install(Locations)
    install(ContentNegotiation) {
        json()
    }
    val app = this
    DI {
        usersConfig
        bind<Application>() with singleton { app }
        bind<UserAuthenticationRoutes>() with eagerSingleton { UserAuthenticationRoutes(di) }
        bind<UsersRoutes>() with eagerSingleton { UsersRoutes(di) }
        bind<UserAuthenticationService>() with eagerSingleton {
            UserAuthenticationServiceImpl(
                userRepository = instance(),
                passwordService = instance()
            )
        }
        bind<UserManagementService>() with eagerSingleton {
            UserManagementServiceImpl(
                userRepository = instance(),
                passwordService = instance()
            )
        }
        bind<UserRepository>() with eagerSingleton { UserRepositoryImpl() }
        bind<PasswordService>() with eagerSingleton { PasswordService() }
    }
}