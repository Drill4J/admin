package com.epam.drill.admin.auth.config

import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.repository.impl.EnvUserRepository
import com.epam.drill.admin.auth.service.*
import com.epam.drill.admin.auth.service.impl.*
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
    bind<UserAuthenticationService>() with eagerSingleton {
        UserAuthenticationServiceImpl(
            instance(),
            instance()
        )
    }
    bind<UserManagementService>() with eagerSingleton { UserManagementServiceImpl(instance(), instance()) }
    bind<PasswordGenerator>() with singleton { PasswordGeneratorImpl(config = instance()) }
    bind<PasswordValidator>() with singleton { PasswordValidatorImpl(config = instance()) }
    bind<PasswordService>() with singleton { PasswordServiceImpl(instance(), instance()) }
}

fun DI.Builder.userRepositoriesConfig() {
    bind<UserRepository>() with eagerSingleton {
        EnvUserRepository(
            env = instance<Application>().environment.config,
            passwordService = instance()
        )
    }
}