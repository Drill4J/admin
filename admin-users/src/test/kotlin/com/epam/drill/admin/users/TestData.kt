package com.epam.drill.admin.users

import com.epam.drill.admin.users.entity.UserEntity

val userGuest = UserEntity(id = 1, username = "guest", passwordHash = "hash", role = "UNDEFINED")
val userAdmin = UserEntity(id = 1, username = "admin", passwordHash = "hash1", role = "ADMIN")
val userUser = UserEntity(id = 2, username = "user", passwordHash = "hash2", role = "USER")