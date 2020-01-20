package com.epam.drill.admin.jwt.user.source

import com.epam.drill.admin.jwt.user.*
import io.ktor.auth.*

class UserSourceImpl : UserSource {

    val testUser = User(1, "guest", "", "admin")

    override fun findUserById(id: Int): User = users.getValue(id)

    override fun findUserByCredentials(credential: UserPasswordCredential): User = testUser

    private val users = listOf(testUser).associateBy(User::id)


}
