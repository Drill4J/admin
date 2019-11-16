@file:Suppress("UNCHECKED_CAST", "unused", "UNUSED_PARAMETER", "DEPRECATION")

package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.endpoints.agent.*
import com.epam.drill.router.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.server.testing.*
import kotlinx.coroutines.sync.*
import org.junit.jupiter.api.*
import java.net.*
import java.time.Duration.*
import kotlin.collections.set

abstract class E2EPluginTest : AdminTest() {
    val mut = Mutex()


    inline fun <reified X : PluginStreams> createSimpleAppWithPlugin(
        uiStreamDebug: Boolean = false,
        agentStreamDebug: Boolean = false,
        noinline block: suspend TestContext<X>.() -> Unit
    ) {
        assertTimeout(ofSeconds(10)) { pluginRun(block, uiStreamDebug, agentStreamDebug) }
    }


    fun TestApplicationEngine.register(
        agentId: String,
        token: String = globToken,
        payload: AgentRegistrationInfo = AgentRegistrationInfo("xz", "ad", "sad")
    ) =
        handleRequest(HttpMethod.Post, "/api" + application.locations.href(Routes.Api.Agent.RegisterAgent(agentId))) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
            setBody(AgentRegistrationInfo.serializer() stringify payload)
        }.run { response.status() to response.content }

    fun TestApplicationEngine.addPlugin(agentId: String, payload: PluginId, token: String = globToken) =
        handleRequest(HttpMethod.Post, "/api" + application.locations.href(Routes.Api.Agent.AddNewPlugin(agentId))) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
            setBody(PluginId.serializer() stringify payload)
        }.run { response.status() to response.content }

    fun TestApplicationEngine.unRegister(agentId: String, token: String = globToken) =
        handleRequest(HttpMethod.Post, "/api" + application.locations.href(Routes.Api.Agent.UnregisterAgent(agentId))) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
        }.run { response.status() to response.content }

    fun TestApplicationEngine.unLoadPlugin(agentId: String, payload: PluginId, token: String = globToken) {
        handleRequest(
            HttpMethod.Post,
            "/api" + application.locations.href(Routes.Api.Agent.UnloadPlugin(agentId, payload.pluginId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
        }.run { response.status() to response.content }
    }

    fun TestApplicationEngine.togglePlugin(agentId: String, pluginId: PluginId, token: String = globToken) {
        handleRequest(
            HttpMethod.Post,
            "/api" + application.locations.href(Routes.Api.Agent.TogglePlugin(agentId, pluginId.pluginId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
        }.run { response.status() to response.content }
    }

    fun TestApplicationEngine.toggleAgent(agentId: String, token: String = globToken) {
        handleRequest(
            HttpMethod.Post,
            "/api" + application.locations.href(Routes.Api.Agent.AgentToggleStandby(agentId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
        }.run { response.status() to response.content }
    }

    fun TestApplicationEngine.renameBuildVersion(
        agentId: String,
        token: String = globToken,
        payload: AgentBuildVersionJson
    ) {
        handleRequest(
            HttpMethod.Post,
            "/api" + application.locations.href(Routes.Api.Agent.RenameBuildVersion(agentId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
            setBody(AgentBuildVersionJson.serializer() stringify payload)
        }.run { response.status() to response.content }
    }


}

data class PluginTestContext(
    val agentId: String,
    val pluginId: String,
    val buildVersionHash: String,
    val token: String,
    val classesCount: Int,
    val engine: TestApplicationEngine,
    var lis: MutableList<Class<*>> = mutableListOf()
) {

    fun pluginAction(
        payload: String,
        pluginId: String = this.pluginId,
        agentId: String = this.agentId,
        token: String = this.token

    ) = engine.handleRequest(
        HttpMethod.Post,
        "/api" + engine.application.locations.href(Routes.Api.Agent.DispatchPluginAction(agentId, pluginId))
    ) {
        addHeader(HttpHeaders.Authorization, "Bearer $token")
        setBody(payload)
    }.run { response.status() to response.content }

}


class MemoryClassLoader : URLClassLoader(arrayOf()) {
    val sw: MutableList<Class<*>> = mutableListOf()
    private val definitions = mutableMapOf<String, ByteArray?>()
    private val mainDefinitions = mutableMapOf<String, ByteArray?>()

    fun addDefinition(name: String, bytes: ByteArray) {
        definitions[name] = bytes
    }

    fun addMainDefinition(name: String, bytes: ByteArray) {
        definitions[name] = bytes
    }

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        val bytes = definitions[name]
        val clazz = if (bytes != null) {
            try {
                defineClass(name, bytes, 0, bytes.size)
            } catch (ex: Throwable) {
                super.loadClass(name, resolve)
            }
        } else {
            super.loadClass(name, resolve)
        }
        sw.add(clazz)
        return clazz
    }
}
