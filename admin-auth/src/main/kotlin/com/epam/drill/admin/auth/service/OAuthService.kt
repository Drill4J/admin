package com.epam.drill.admin.auth.service

import com.epam.drill.admin.auth.model.UserInfoView
import io.ktor.auth.*

interface OAuthService {
    suspend fun signInThroughOAuth(principal: OAuthAccessTokenResponse.OAuth2): UserInfoView
}