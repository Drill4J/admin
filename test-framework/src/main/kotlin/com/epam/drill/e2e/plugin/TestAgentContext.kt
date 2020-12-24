package com.epam.drill.e2e.plugin

import com.epam.drill.plugin.api.processing.*
import kotlinx.atomicfu.*

class TestAgentContext : AgentContext {

    private val _data = atomic<Pair<String, String>?>(null)

    override fun invoke(): String? = _data.value?.first

    override fun get(key: String): String? = _data.value?.second

    fun runWithSession(sessionId: String, block: () -> Unit) {
        _data.value = sessionId to "xxxx"
        try {
            block()
        } finally {
            _data.value = null
        }

    }
}
