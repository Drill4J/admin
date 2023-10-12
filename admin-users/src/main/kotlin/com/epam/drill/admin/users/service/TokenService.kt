package com.epam.drill.admin.users.service

import com.epam.drill.admin.users.view.UserView

interface TokenService {
    fun issueToken(user: UserView): String
}