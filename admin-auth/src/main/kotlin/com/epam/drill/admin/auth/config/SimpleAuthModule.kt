package com.epam.drill.admin.auth.config

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import com.epam.drill.admin.auth.model.LoginPayload
import com.epam.drill.admin.auth.model.UserView
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.principal.User
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.repository.impl.DatabaseUserRepository
import com.epam.drill.admin.auth.repository.impl.EnvUserRepository
import com.epam.drill.admin.auth.service.*
import com.epam.drill.admin.auth.service.impl.*
import com.epam.drill.admin.auth.service.transaction.TransactionalUserAuthenticationService
import com.epam.drill.admin.auth.service.transaction.TransactionalUserManagementService
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.*
import io.ktor.http.auth.*
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di
import org.kodein.di.singleton

private val logger = KotlinLogging.logger {}

enum class UserRepoType {
    DB,
    ENV
}

fun Application.simpleAuthModule(diConfigure: DI.MainBuilder.() -> Unit = {}) {
    di {
        bind<JwtConfig>() with singleton { JwtConfig(di) }
        bind<JWTVerifier>() with singleton { buildJwkVerifier(instance()) }
        bind<TokenService>() with singleton { JwtTokenService(instance()) }
        bind<PasswordStrengthConfig>() with singleton { PasswordStrengthConfig(di) }

        userRepositoriesConfig()
        userServicesConfig()

        diConfigure()
    }

    install(Authentication) {
        configureJwt(closestDI())
        configureBasic(closestDI())
    }
}

private fun buildJwkVerifier(jwtConfig: JwtConfig) = JWT
    .require(Algorithm.HMAC512(jwtConfig.secret))
    .withIssuer(jwtConfig.issuer)
    .build()


private fun Authentication.Configuration.configureJwt(di: DI) {
    val jwtConfig by di.instance<JwtConfig>()
    val jwtVerifier by di.instance<JWTVerifier>()

    jwt("jwt") {
        realm = "Access to the http(s) and the ws(s) services"
        verifier(jwtVerifier)
        validate {
            it.payload.toPrincipal()
        }
        authHeader { call ->
            val headerValue = call.request.headers[HttpHeaders.Authorization]
                ?: "Bearer ${call.request.cookies["jwt"] ?: call.parameters["token"]}"
            parseAuthorizationHeader(headerValue)
        }
    }
}

private fun Authentication.Configuration.configureBasic(di: DI) {
    val authService by di.instance<UserAuthenticationService>()

    basic("basic") {
        realm = "Access to the http(s) services"
        validate {
            authService.signIn(LoginPayload(username = it.name, password = it.password)).toPrincipal()
        }
    }
}

private fun DI.Builder.userServicesConfig() {
    bind<UserAuthenticationService>() with singleton {
        val app: Application = instance()
        UserAuthenticationServiceImpl(
            userRepository = instance(),
            passwordService = instance()
        ).let { service ->
            if (app.userRepoType == UserRepoType.DB)
                TransactionalUserAuthenticationService(service)
            else
                service
        }
    }
    bind<UserManagementService>() with singleton {
        val app: Application = instance()
        UserManagementServiceImpl(
            userRepository = instance(),
            passwordService = instance()
        ).let { service ->
            if (app.userRepoType == UserRepoType.DB)
                TransactionalUserManagementService(service)
            else
                service
        }
    }
    bind<PasswordGenerator>() with singleton { PasswordGeneratorImpl(config = instance()) }
    bind<PasswordValidator>() with singleton { PasswordValidatorImpl(config = instance()) }
    bind<PasswordService>() with singleton { PasswordServiceImpl(instance(), instance()) }
}

private fun DI.Builder.userRepositoriesConfig() {
    bind<UserRepository>() with singleton {
        val app: Application = instance()
        logger.info { "The user repository type is ${app.userRepoType}" }
        when (app.userRepoType) {
            UserRepoType.DB -> DatabaseUserRepository()
            UserRepoType.ENV -> EnvUserRepository(
                env = instance<Application>().environment.config,
                passwordService = instance()
            )
        }
    }
}

private val Application.userRepoType: UserRepoType
    get() = environment.config
        .config("drill")
        .config("auth")
        .propertyOrNull("userRepoType")
        ?.getString()?.let { UserRepoType.valueOf(it) }
        ?: UserRepoType.DB

private fun Payload.toPrincipal(): User {
    return User(
        username = subject,
        role = Role.valueOf(getClaim("role").asString())
    )
}

private fun UserView.toPrincipal(): User {
    return User(
        username = username,
        role = role
    )
}
