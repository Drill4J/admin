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
package com.epam.drill.admin.config

import io.ktor.config.*

class DatabaseConfig(private val config: ApplicationConfig) {
    val host: String
        get() = config.propertyOrNull("host")?.getString() ?: "localhost"

    val port: Int
        get() = config.propertyOrNull("port")?.getString()?.toInt() ?: 5432

    val databaseName: String
        get() = config.propertyOrNull("dbName")?.getString() ?: "drill"

    val username: String
        get() = config.propertyOrNull("userName")?.getString() ?: "postgres"

    val password: String
        get() = config.propertyOrNull("password")?.getString() ?: "postgres"

    val maxPoolSize: Int
        get() = config.propertyOrNull("maximumPoolSize")?.getString()?.toInt() ?: 20

}