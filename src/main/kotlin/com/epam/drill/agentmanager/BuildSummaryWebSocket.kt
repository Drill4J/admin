package com.epam.drill.agentmanager

import com.epam.drill.common.*
import kotlinx.serialization.*

@Serializable
data class BuildSummaryWebSocket(
    val buildVersion: String,
    val alias: String,
    val addedDate: Long,
    val totalMethods: Int,
    val newMethods: Int,
    val modifiedMethods: Int,
    val unaffectedMethods: Int,
    val deletedMethods: Int
)

fun BuildSummary.toWebSocketSummary(agentBuilds: Set<AgentBuildVersionJson>) = BuildSummaryWebSocket(
    buildVersion = this.name,
    alias = agentBuilds.firstOrNull { it.id == this.name }?.name ?: "",
    addedDate = this.addedDate,
    totalMethods = this.totalMethods,
    newMethods = this.newMethods,
    modifiedMethods = this.modifiedMethods,
    unaffectedMethods = this.unaffectedMethods,
    deletedMethods = this.deletedMethods
)
