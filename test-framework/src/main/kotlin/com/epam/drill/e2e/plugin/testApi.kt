package com.epam.drill.e2e.plugin

import com.epam.drill.e2e.*

@Suppress("unused")
fun AdminTest.runWithSession(
    sessionId: String,
    block: () -> Unit
) = testAgentContext.runWithSession(sessionId, block)
