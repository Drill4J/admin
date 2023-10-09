package com.epam.drill.admin.users.service.impl

import com.epam.drill.admin.users.entity.UserEntity
import com.epam.drill.admin.users.model.Role
import com.epam.drill.admin.users.model.UserStatus
import com.epam.drill.admin.users.view.CredentialsView
import com.epam.drill.admin.users.view.RegistrationForm
import com.epam.drill.admin.users.view.UserView

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