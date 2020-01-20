package com.epam.drill.admin.jwt.user.source

import com.epam.drill.admin.jwt.user.*
import io.ktor.auth.*

interface UserSource {
    fun findUserById(id: Int): User

    fun findUserByCredentials(credential: UserPasswordCredential): User
}
