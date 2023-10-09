/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.users.repository.impl

import com.epam.drill.admin.users.entity.UserEntity
import com.epam.drill.admin.users.repository.UserRepository

class UserRepositoryImpl: UserRepository {
    override fun findAll(): List<UserEntity> {
        TODO("Not yet implemented")
    }

    override fun findById(id: Int): UserEntity? {
        TODO("Not yet implemented")
    }

    override fun findByUsername(username: String): UserEntity? {
        TODO("Not yet implemented")
    }

    override fun create(entity: UserEntity) {
        TODO("Not yet implemented")
    }

    override fun update(entity: UserEntity) {
        TODO("Not yet implemented")
    }
}