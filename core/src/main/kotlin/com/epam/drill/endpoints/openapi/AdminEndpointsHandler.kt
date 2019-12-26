package com.epam.drill.endpoints.openapi

import com.epam.drill.admin.agent.*
import com.epam.drill.common.*

import com.epam.drill.endpoints.*
import io.ktor.http.*
import org.kodein.di.*
import org.kodein.di.generic.*


class AdminEndpointsHandler(override val kodein: Kodein) : KodeinAware {
    private val agentManager: AgentManager by instance()

    suspend fun updateBuildAliasWithResult(
        agentId: String,
        buildVersion: AgentBuildVersionJson
    ): HttpStatusCode {
        val buildManager = agentManager.adminData(agentId).buildManager
        return when {
            buildManager[buildVersion.id] == null -> HttpStatusCode.NotFound
            buildManager.buildAliasExists(buildVersion.name) -> HttpStatusCode.BadRequest
            else -> {
                agentManager.updateAgentBuildAliases(agentId, buildVersion)
                HttpStatusCode.OK
            }
        }
    }

    suspend fun updateSystemSettings(
        agentId: String,
        systemSettings: SystemSettingsDto
    ): HttpStatusCode {
        return when {
            systemSettings.packagesPrefixes.any { it.isBlank() } -> HttpStatusCode.BadRequest

            else -> {
                agentManager.updateSystemSettings(agentId, systemSettings)
                HttpStatusCode.OK
            }
        }
    }
}
