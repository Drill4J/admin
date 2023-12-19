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
package com.epam.drill.admin.auth.model

import com.epam.drill.admin.auth.config.*
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.principal.User
import kotlinx.serialization.*
import kotlinx.datetime.LocalDateTime

@Serializable
data class DataResponse<T>(val data: T, val message: String? = null)

@Serializable
data class MessageResponse(val message: String)

@Serializable
data class TokenView(val token: String)

@Serializable
data class UserInfoView(
    val id: Int,
    val username: String,
    val role: Role,
    val external: Boolean
)

fun UserInfoView.toPrincipal(): User {
    return User(
        id = id,
        username = username,
        role = role
    )
}

@Serializable
data class UserView(
    val id: Int,
    val username: String,
    val role: Role,
    val blocked: Boolean,
    val registrationDate: LocalDateTime?,
    val external: Boolean
)

@Serializable
data class CredentialsView(
    val username: String,
    val password: String,
)

@Serializable
data class EditUserPayload(
    val role: Role
)

@Serializable
data class LoginPayload(
    val username: String,
    val password: String
)

@Serializable
data class RegistrationPayload(
    val username: String,
    val password: String
)

@Serializable
data class ChangePasswordPayload(
    val oldPassword: String,
    val newPassword: String
)

fun UserEntity.toUserInfoView(): UserInfoView {
    return UserInfoView(
        id = this.id ?: throw IllegalStateException("User id cannot be null"),
        username = this.username,
        role = Role.valueOf(this.role),
        external = this.external
    )
}

@Serializable
data class AuthConfigView(
    val simpleAuth: SimpleAuthConfigView? = null,
    val oauth2: OAuth2ConfigView? = null
)

@Serializable
data class SimpleAuthConfigView(
    val enabled: Boolean,
    val signUpEnabled: Boolean
)

@Serializable
data class OAuth2ConfigView(
    val enabled: Boolean,
    val buttonTitle: String,
    val automaticSignIn: Boolean
)

fun AuthConfig.toView() = AuthConfigView(
    simpleAuth = simpleAuth?.toView(),
    oauth2 = oauth2?.toView()
)

fun OAuth2Config.toView() = OAuth2ConfigView(
    enabled = enabled,
    buttonTitle = signInButtonTitle,
    automaticSignIn = automaticSignIn
)

fun SimpleAuthConfig.toView() = SimpleAuthConfigView(
    enabled = enabled,
    signUpEnabled = signUpEnabled
)