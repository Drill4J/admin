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
package com.epam.drill.admin.auth.service.transaction

import com.epam.drill.admin.auth.model.UserInfoView
import com.epam.drill.admin.auth.service.OAuthService
import com.epam.drill.admin.auth.config.AuthDatabaseConfig.transaction
import io.ktor.server.auth.*

class TransactionalOAuthService(private val delegate: OAuthService): OAuthService by delegate {
    override suspend fun signInThroughOAuth(principal: OAuthAccessTokenResponse.OAuth2): UserInfoView = transaction {
        delegate.signInThroughOAuth(principal)
    }
}