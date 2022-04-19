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
package com.epam.drill.admin.api.websocket

import kotlinx.serialization.*

//todo EPMDJ-10338 remove OutputType?
enum class OutputType { LIST, DEFAULT }

enum class FieldOp { EQ, CONTAINS }

enum class OrderKind { ASC, DESC }

@Serializable
data class FieldFilter(val field: String, val value: String, val op: FieldOp = FieldOp.EQ)

@Serializable
data class FieldOrder(val field: String, val order: OrderKind = OrderKind.ASC)

@Serializable
sealed class Subscription {
    abstract val output: OutputType
    abstract val pagination: Pagination
    abstract val filters: Set<FieldFilter>
    abstract val orderBy: Set<FieldOrder>
}

@Serializable
data class Pagination(
    val pageIndex: Int,
    val pageSize: Int,
) {
    companion object {
        val empty = Pagination(-1, -1)
    }
}

@Serializable
@SerialName("AGENT")
data class AgentSubscription(
    val agentId: String,
    val buildVersion: String? = null,
    val filterId: String = "",
    override val output: OutputType = OutputType.DEFAULT,
    override val pagination: Pagination = Pagination.empty,
    override val filters: Set<FieldFilter> = emptySet(),
    override val orderBy: Set<FieldOrder> = emptySet(),
) : Subscription()

@Serializable
@SerialName("GROUP")
data class GroupSubscription(
    val groupId: String,
    override val output: OutputType = OutputType.DEFAULT,
    override val pagination: Pagination = Pagination.empty,
    override val filters: Set<FieldFilter> = emptySet(),
    override val orderBy: Set<FieldOrder> = emptySet(),
) : Subscription()
