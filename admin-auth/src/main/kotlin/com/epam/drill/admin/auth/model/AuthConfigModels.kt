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