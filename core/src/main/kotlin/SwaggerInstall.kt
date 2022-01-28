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
package com.epam.drill.admin

import com.epam.drill.admin.api.agent.*
import de.nielsfalk.ktor.swagger.*
import de.nielsfalk.ktor.swagger.version.shared.*
import de.nielsfalk.ktor.swagger.version.v2.*
import de.nielsfalk.ktor.swagger.version.v3.*
import io.ktor.application.*

fun Application.enableSwaggerSupport() {
    install(SwaggerSupport) {
        val agentIdSchema = mapOf(
            "type" to "String"
        )
        forwardRoot = true
        val information = Information(
            version = "1.0",
            title = "Drill4J ktor API",
            description = "This is Drill4J ktor API ([project sources](https://github.com/Drill4J))",
            contact = Contact(
                name = "Drill4J Project",
                url = "https://drill4j.github.io/"
            )
        )
        swagger = Swagger().apply {
            info = information
            definitions["agentId"] = agentIdSchema
        }
        openApi = OpenApi().apply {
            info = information
            components.schemas["agentId"] = agentIdSchema
        }
    }
}

val agentUpdateExample = AgentUpdateDto(
    name = "Petclinic",
    description = "",
    environment = ""
)

val agentRegistrationExample = AgentRegistrationDto(
    name = "Petclinic",
    description = "Simple web service",
    environment = "",
    systemSettings = SystemSettingsDto(
        packages = listOf("org/springframework/samples/petclinic")
    ),
    plugins = mutableListOf("test-to-code-mapping")
)
