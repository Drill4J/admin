package com.epam.drill.admin.users.repository

import com.epam.drill.admin.users.entity.UserEntity

interface UserRepository {
    fun findAll()

    fun findById(id: Int)

    fun create(entity: UserEntity)

    fun update(entity: UserEntity)

}