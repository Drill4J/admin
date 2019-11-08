package com.epam.drill.testdata

import com.epam.drill.common.*

const val agentId = "testAgent"

const val sslPort = "8443"

val pluginMetadata = PluginMetadata(
    id = "test-to-code-mapping",
    name = "test-to-code-mapping",
    description = "test",
    type = "test",
    family = Family.INSTRUMENTATION,
    enabled = true,
    config = "config",
    md5Hash = "",
    isNative = false

)

val ai = AgentInfo(
    id = agentId,
    name = agentId,
    status = AgentStatus.ONLINE,
    groupName = "",
    description = "",
    buildVersion = "",
    buildAlias = "",
    adminUrl = "",
    plugins = mutableSetOf(
        pluginMetadata
    )
)

val pluginT2CM = PluginId("test-to-code-mapping")

