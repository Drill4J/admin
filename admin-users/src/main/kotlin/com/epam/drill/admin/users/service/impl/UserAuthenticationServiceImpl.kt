package com.epam.drill.admin.users.service.impl

import com.epam.drill.admin.users.repository.UserRepository
import com.epam.drill.admin.users.service.UserAuthenticationService
import com.epam.drill.admin.users.view.ChangePasswordForm
import com.epam.drill.admin.users.view.LoginForm
import com.epam.drill.admin.users.view.RegistrationForm
import com.epam.drill.admin.users.view.TokenResponse

class UserAuthenticationServiceImpl(val userRepository: UserRepository): UserAuthenticationService {
    override fun signIn(form: LoginForm): TokenResponse {
        return TokenResponse("token")
    }

    override fun signUp(form: RegistrationForm) {

    }

    override fun updatePassword(form: ChangePasswordForm) {

    }

}