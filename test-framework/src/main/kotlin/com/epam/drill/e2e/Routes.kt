package com.epam.drill.e2e

import com.epam.drill.admin.api.routes.*

internal fun <T> agentApi(block: (ApiRoot.Agents) -> T): T = run {
    ApiRoot().let(ApiRoot::Agents).let(block)
}

internal fun <T> groupApi(id: String, block: (ApiRoot.ServiceGroup) -> T): T = run {
    ApiRoot().let { ApiRoot.ServiceGroup(it, id) }.let(block)
}
