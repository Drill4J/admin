package com.epam.drill.admin.api.websocket

import kotlinx.serialization.*

enum class FieldOp { EQ, CONTAINS }

enum class OrderKind { ASC, DESC }

@Serializable
data class FieldFilter(val field: String, val value: String, val op: FieldOp = FieldOp.EQ)

@Serializable
data class FieldOrder(val field: String, val order: OrderKind = OrderKind.ASC)

@Serializable
data class SearchStatement(val fieldName: String, val value: String) //TODO remove

@Serializable
data class SortStatement(val fieldName: String, val order: String) //TODO remove

@Serializable
sealed class Subscription {
    abstract val filters: Set<FieldFilter>
    abstract val orderBy: Set<FieldOrder>
}

@Serializable
@SerialName("AGENT")
data class AgentSubscription(
    val agentId: String,
    val buildVersion: String? = null,
    val searchStatement: SearchStatement? = null,//TODO remove
    val sortStatement: SortStatement? = null,//TODO remove
    override val filters: Set<FieldFilter> = emptySet(),
    override val orderBy: Set<FieldOrder> = emptySet()
) : Subscription()

@Serializable
@SerialName("GROUP")
data class GroupSubscription(
    val groupId: String,
    override val filters: Set<FieldFilter> = emptySet(),
    override val orderBy: Set<FieldOrder> = emptySet()
) : Subscription()
