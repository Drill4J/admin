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
package com.epam.drill.admin.security

import com.epam.drill.admin.jwt.config.jwtConfig
import com.epam.drill.admin.userSource
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*

fun Application.installAuthentication() {
    install(Authentication) {
        jwt("jwt") {
            realm = "Access to the http(s) and the ws(s) services"
            verifier(jwtConfig.verifier)
            validate {
                it.payload.getClaim("id").asInt()?.let(userSource::findUserById)
            }
        }
        basic("basic") {
            realm = "Access to the http(s) services"
            validate {
                userSource.findUserByCredentials(it)
            }
        }
    }
}