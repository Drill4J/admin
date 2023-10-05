package com.epam.drill.admin.users.service

import com.epam.drill.admin.users.view.CredentialsView
import com.epam.drill.admin.users.view.UserForm
import com.epam.drill.admin.users.view.UserView

interface UserManagementService {
    fun getUsers(): List<UserView>

    fun getUser(userId: Int)

    fun updateUser(userId: Int, form: UserForm)

    fun deleteUser(userId: Int)

    fun blockUser(userId: Int)

    fun unblockUser(userId: Int)

    fun resetPassword(userId: Int): CredentialsView

}