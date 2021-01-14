package com.epam.drill.e2e

import com.epam.drill.admin.common.serialization.*
import com.epam.drill.common.*
import io.ktor.server.testing.*
import kotlinx.serialization.protobuf.*


fun wsRequestRequiredParams(
    ag: AgentWrap
): TestApplicationRequest.() -> Unit {
    return {
        this.addHeader(
            AgentConfigParam,
            ProtoBuf.dumps(
                AgentConfig.serializer(),
                AgentConfig(
                    ag.id,
                    ag.instanceId,
                    ag.buildVersion,
                    ag.serviceGroupId,
                    AgentType.valueOf(ag.agentType.name),
                    ag.buildVersion,
                    ag.needSync
                )
            )
        )
        this.addHeader(NeedSyncParam, ag.needSync.toString())
    }
}
