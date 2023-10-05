package com.epam.drill.admin.users.service

import com.epam.drill.admin.users.view.*

interface UserAuthenticationService {
    fun signIn(form: LoginForm): TokenResponse

    fun signUp(form: RegistrationForm)

    fun updatePassword(form: ChangePasswordForm)
}