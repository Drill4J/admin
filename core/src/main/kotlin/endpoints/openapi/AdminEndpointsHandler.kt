package com.epam.drill.admin.endpoints.openapi

import com.epam.drill.admin.agent.*
import com.epam.drill.common.*

import com.epam.drill.admin.endpoints.*
import io.ktor.http.*
import org.kodein.di.*
import org.kodein.di.generic.*


class AdminEndpointsHandler(override val kodein: Kodein) : KodeinAware {
    private val agentManager by instance<AgentManager>()

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
