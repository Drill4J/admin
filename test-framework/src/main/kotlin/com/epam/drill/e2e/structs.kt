package com.epam.drill.e2e

import com.epam.drill.builds.*

data class AgentAsyncStruct(
    val ag: AgentWrap,
    val build: Build,
    val callback: suspend PluginTestContext.(Any, Any) -> Unit,
    val thenCallbacks: MutableList<ThenAgentAsyncStruct> = mutableListOf()
)

data class ThenAgentAsyncStruct(
    val ag: AgentWrap,
    val build: Build,
    val callback: suspend PluginTestContext.(Any, Any) -> Unit
)


class AgentDatum(val classMap: Map<String, ByteArray>)
