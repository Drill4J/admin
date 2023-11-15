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

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import com.epam.drill.admin.auth.model.LoginPayload
import com.epam.drill.admin.auth.model.UserView
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.principal.User
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.model.LoginPayload
import com.epam.drill.admin.auth.model.UserInfoView
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.auth.*
import org.kodein.di.*

private fun Payload.toPrincipal(): User {
    return User(
        username = subject,
        role = Role.valueOf(getClaim("role").asString())
    )
}

private fun UserInfoView.toPrincipal(): User {
    return User(
        username = username,
        role = role
    )
}
