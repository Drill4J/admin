package com.epam.drill

import com.epam.drill.agentmanager.*
import com.epam.drill.common.*
import com.epam.drill.endpoints.agent.*
import com.epam.drill.plugins.*
import de.nielsfalk.ktor.swagger.*
import de.nielsfalk.ktor.swagger.version.shared.*
import de.nielsfalk.ktor.swagger.version.v2.*
import de.nielsfalk.ktor.swagger.version.v3.*
import io.ktor.application.*


private val agentIdSchema = mapOf<String, String>(
    "type" to "String"
)


fun Application.enableSwaggerSupport() {
    install(SwaggerSupport) {
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

val agentInfoWebSocketExample = AgentInfoWebSocket(
    id = "Petclinic",
    name = "Petclinic",
    status = AgentStatus.NOT_REGISTERED,
    buildVersion = "0.0.1",
    plugins = mutableSetOf(PluginWebSocket(id = "", relation = null)),
    buildAlias = "0.1.0",
    description = "",
    packagesPrefixes = listOf("org/springframework/samples/petclinic"),
    group = "",
    agentType = "Java",
    instanceIds = mutableSetOf()
)

val agentRegistrationExample = AgentRegistrationInfo(
    name = "Petclinic",
    description = "Simple web service",
    group = "",
    sessionIdHeaderName = "",
    plugins = mutableListOf("test-to-code-mapping"),
    packagesPrefixes = listOf("org/springframework/samples/petclinic")
)




