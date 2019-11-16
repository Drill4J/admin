package com.epam.drill.e2e.plugin

import com.epam.drill.plugin.api.processing.*
import io.mockk.*

fun runWithSession(sessionId: String, block: () -> Unit) {
    mockkObject(DrillContext)
    every { DrillContext[any()] } returns "xxxx"
    every { DrillContext.invoke() } returns sessionId
    block()
    unmockkObject(DrillContext)
}