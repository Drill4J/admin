package com.epam.drill.admin.auth.service.transaction

import com.epam.drill.admin.auth.model.UserInfoView
import com.epam.drill.admin.auth.service.OAuthService
import com.epam.drill.admin.auth.config.DatabaseConfig.transaction
import io.ktor.auth.*

class TransactionalOAuthService(private val delegate: OAuthService): OAuthService by delegate {
    override suspend fun signInThroughOAuth(principal: OAuthAccessTokenResponse.OAuth2): UserInfoView = transaction {
        delegate.signInThroughOAuth(principal)
    }
}