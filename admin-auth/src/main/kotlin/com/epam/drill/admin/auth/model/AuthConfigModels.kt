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

import com.epam.drill.admin.auth.config.AuthConfig
import com.epam.drill.admin.auth.config.OAuth2Config
import com.epam.drill.admin.auth.config.SimpleAuthConfig
import kotlinx.serialization.Serializable

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