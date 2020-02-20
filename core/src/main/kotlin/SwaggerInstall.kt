package com.epam.drill.admin

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.common.*
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

val agentInfoWebSocketExample = AgentInfoDto(
    id = "Petclinic",
    serviceGroup = "",
    name = "Petclinic",
    status = AgentStatus.NOT_REGISTERED,
    buildVersion = "0.0.1",
    plugins = setOf(PluginDto(id = "", relation = "")),
    description = "",
    packagesPrefixes = listOf("org/springframework/samples/petclinic"),
    environment = "",
    agentType = "Java",
    instanceIds = mutableSetOf()
)

val agentRegistrationExample = AgentRegistrationInfo(
    name = "Petclinic",
    description = "Simple web service",
    environment = "",
    sessionIdHeaderName = "",
    plugins = mutableListOf("test-to-code-mapping"),
    packagesPrefixes = listOf("org/springframework/samples/petclinic")
)
