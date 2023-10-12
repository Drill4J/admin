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
package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.jwt.JwtTokenService
import com.epam.drill.admin.auth.jwt.bindJwt
import com.epam.drill.admin.auth.jwt.toPrincipal
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.service.impl.toPrincipal
import com.epam.drill.admin.auth.view.LoginForm
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import org.kodein.di.*

val securityDiConfig: DI.Builder.(Application) -> Unit
    get() = { _ ->
        bindJwt()
        bind<SecurityConfig>() with eagerSingleton { SecurityConfig(di) }
    }

class SecurityConfig(override val di: DI) : DIAware {
    private val app by instance<Application>()
    private val authService by instance<UserAuthenticationService>()
    private val jwtTokenService by instance<JwtTokenService>()

    init {
        app.install(Authentication) {
            configureJwt("jwt")
            configureBasic("basic")
        }
    }

    private fun Authentication.Configuration.configureBasic(name: String? = null) {
        basic(name) {
            realm = "Access to the http(s) services"
            validate {
                authService.signIn(LoginForm(username = it.name, password = it.password)).toPrincipal()
            }
        }
    }

    private fun Authentication.Configuration.configureJwt(name: String? = null) {
        jwt(name) {
            realm = "Access to the http(s) and the ws(s) services"
            verifier(jwtTokenService.verifier)
            validate {
                it.payload.toPrincipal()
            }
        }
    }
}
