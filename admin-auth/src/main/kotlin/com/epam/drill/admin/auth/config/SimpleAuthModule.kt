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

import com.epam.drill.admin.auth.model.LoginPayload
import com.epam.drill.admin.auth.model.toPrincipal
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.repository.impl.DatabaseUserRepository
import com.epam.drill.admin.auth.service.*
import com.epam.drill.admin.auth.service.impl.*
import com.epam.drill.admin.auth.service.transaction.TransactionalUserAuthenticationService
import com.epam.drill.admin.auth.service.transaction.TransactionalUserManagementService
import io.ktor.application.*
import io.ktor.auth.*
import org.kodein.di.*

val simpleAuthDIModule = DI.Module("simpleAuth") {
    importOnce(jwtServicesDIModule)
    importOnce(userServicesDIModule)
    importOnce(simpleAuthConfigDIModule)
}

val simpleAuthConfigDIModule = DI.Module("simpleAuthConfig") {
    bind<SimpleAuthConfig>() with singleton {
        SimpleAuthConfig(instance<Application>().environment.config.config("drill.auth.simpleAuth"))
    }
}

val authConfigDIModule = DI.Module("authConfig") {
    bind<AuthConfig>() with singleton {
        AuthConfig(
            config = instance<Application>().environment.config.config("drill.auth"),
            oauth2 = instanceOrNull(),
            simpleAuth = instanceOrNull(),
            jwt = instance(),
        )
    }
}


/**
 * A Ktor Authentication plugin configuration for Basic based authentication.
 */
fun Authentication.Configuration.configureBasicAuthentication(di: DI) {
    val authService by di.instance<UserAuthenticationService>()

    basic("basic") {
        realm = "Access to the http(s) services"
        validate {
            authService.signIn(LoginPayload(username = it.name, password = it.password)).toPrincipal()
        }
    }
}

val userServicesDIModule = DI.Module("userServices") {
    importOnce(userRepositoryDIModule)
    importOnce(passwordHashServiceDIModule)
    importOnce(passwordIssuingServicesDIModule)
    bind<UserAuthenticationService>() with singleton {
        UserAuthenticationServiceImpl(
            userRepository = instance(),
            passwordService = instance(),
            passwordValidator = instance()
        ).let { TransactionalUserAuthenticationService(it) }
    }
    bind<UserManagementService>() with singleton {
        UserManagementServiceImpl(
            userRepository = instance(),
            passwordService = instance(),
            passwordGenerator = instance(),
            externalRoleManagement = isExternalRoleManagement(instanceOrNull<OAuth2Config>())
        ).let { TransactionalUserManagementService(it) }
    }
}

val userRepositoryDIModule = DI.Module("userRepository") {
    bind<UserRepository>() with singleton { DatabaseUserRepository() }
}

val passwordHashServiceDIModule = DI.Module("passwordHashService") {
    bind<PasswordService>() with singleton { PasswordServiceImpl() }
}

val passwordIssuingServicesDIModule = DI.Module("passwordIssuingServices") {
    bind<PasswordStrengthConfig>() with singleton {
        PasswordStrengthConfig(
            instance<Application>().environment.config.config("drill.auth.password")
        )
    }
    bind<PasswordGenerator>() with singleton { PasswordGeneratorImpl(config = instance()) }
    bind<PasswordValidator>() with singleton { PasswordValidatorImpl(config = instance()) }
}

private fun isExternalRoleManagement(config: OAuth2Config?): Boolean {
    return (config?.userInfoUrl == null && config?.tokenMapping?.roles != null) ||
            (config?.userInfoUrl != null && config.userInfoMapping.roles != null)
}
