package com.epam.drill.e2e

import com.epam.drill.common.*
import io.kotlintest.*

class AgentTypeTest : E2ETest() {

    @org.junit.jupiter.api.Test
    fun `check agent type`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(id = "agentId1", agentType = AgentType.JAVA)) { ui, _ ->
                ui.getAgent()?.agentType shouldBe "Java"
            }
            connectAgent(AgentWrap(id = "agentId2", agentType = AgentType.DOTNET)) { ui, _ ->
                ui.getAgent()?.agentType shouldBe ".NET"
            }
        }
    }
}