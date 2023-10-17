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
package com.epam.drill.admin.auth.service.impl

import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.model.Role
import com.epam.drill.admin.auth.model.UserStatus
import com.epam.drill.admin.auth.view.CredentialsView
import com.epam.drill.admin.auth.view.RegistrationForm
import com.epam.drill.admin.auth.view.UserView

fun RegistrationForm.toEntity(passwordHash: String): UserEntity {
    return UserEntity(
        username = this.username,
        passwordHash = passwordHash,
        role = Role.UNDEFINED.name,
    )
}

fun UserEntity.toView(): UserView {
    return UserView(
        username = this.username,
        role = Role.valueOf(this.role),
        status = if (this.blocked) UserStatus.BLOCKED else UserStatus.ACTIVE,
    )
}

fun UserEntity.toCredentialsView(newPassword: String): CredentialsView {
    return CredentialsView(
        username = this.username,
        password = newPassword,
        role = Role.valueOf(this.role)
    )
}