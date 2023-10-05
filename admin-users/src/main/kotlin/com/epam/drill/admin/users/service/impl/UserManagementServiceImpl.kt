package com.epam.drill.admin.users.service.impl

import com.epam.drill.admin.users.repository.UserRepository
import com.epam.drill.admin.users.service.UserManagementService
import com.epam.drill.admin.users.view.CredentialsView
import com.epam.drill.admin.users.view.UserForm
import com.epam.drill.admin.users.view.UserView

class UserManagementServiceImpl(val userRepository: UserRepository): UserManagementService {
    override fun getUsers(): List<UserView> {
        TODO("Not yet implemented")
    }

    override fun getUser(userId: Int) {
        TODO("Not yet implemented")
    }

    override fun updateUser(userId: Int, form: UserForm) {
        TODO("Not yet implemented")
    }

    override fun deleteUser(userId: Int) {
        TODO("Not yet implemented")
    }

    override fun blockUser(userId: Int) {
        TODO("Not yet implemented")
    }

    override fun unblockUser(userId: Int) {
        TODO("Not yet implemented")
    }

    override fun resetPassword(userId: Int): CredentialsView {
        TODO("Not yet implemented")
    }
}