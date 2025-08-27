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

import com.epam.drill.admin.common.principal.User
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.config.AuthDatabaseConfig.transaction
import com.epam.drill.admin.auth.model.*

class TransactionalUserAuthenticationService(
    private val delegate: UserAuthenticationService
) : UserAuthenticationService by delegate {
    override suspend fun signIn(payload: LoginPayload) = transaction {
        delegate.signIn(payload)
    }

    override suspend fun signUp(payload: RegistrationPayload) = transaction {
        delegate.signUp(payload)
    }

    override suspend fun updatePassword(principal: User, payload: ChangePasswordPayload) = transaction {
        delegate.updatePassword(principal, payload)
    }

    override suspend fun getUserInfo(principal: User) = transaction {
        delegate.getUserInfo(principal)
    }
}