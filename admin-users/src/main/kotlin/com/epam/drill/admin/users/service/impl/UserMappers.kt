package com.epam.drill.admin.users.service.impl

import com.epam.drill.admin.users.entity.UserEntity
import com.epam.drill.admin.users.view.CredentialsView
import com.epam.drill.admin.users.view.RegistrationForm
import com.epam.drill.admin.users.view.UserForm
import com.epam.drill.admin.users.view.UserView

fun UserEntity.toModel(): UserView {
    TODO("Not yet implemented")
}

fun RegistrationForm.toEntity(): UserEntity {
    TODO("Not yet implemented")
}

fun UserEntity.toView(): UserView {
    TODO("Not yet implemented")
}

fun UserEntity.toForm(): UserForm {
    TODO("Not yet implemented")
}

fun UserForm.toEntity(userId: Int): UserEntity {
    TODO("Not yet implemented")
}
fun UserEntity.toCredentialsView(newPassword: String): CredentialsView {
    TODO("Not yet implemented")
}