/**
 * Copyright 2020 EPAM Systems
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
