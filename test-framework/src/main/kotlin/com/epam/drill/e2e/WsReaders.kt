package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.e2e.*
import io.ktor.server.testing.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*


fun wsRequestRequiredParams(
    ag: AgentWrap
): TestApplicationRequest.() -> Unit {
    return {
        this.addHeader(
            AgentConfigParam,
            Cbor.dumps(AgentConfig.serializer(), AgentConfig(ag.id, ag.buildVersion, ag.needSync))
        )
        this.addHeader(NeedSyncParam, ag.needSync.toString())
    }
}
