package com.epam.drill.e2e

import com.epam.drill.admin.router.*
import com.epam.drill.common.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.testdata.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import java.net.*
import kotlin.collections.set
import kotlin.coroutines.*
import kotlin.time.*

abstract class E2EPluginTest : AdminTest() {

    @ExperimentalTime
    inline fun <reified X : PluginStreams> createSimpleAppWithPlugin(
        uiStreamDebug: Boolean = false,
        agentStreamDebug: Boolean = false,
        timeout: Long = 20,
        noinline block: suspend TestContext<X>.() -> Unit
    ) = runBlocking {
        val context = SupervisorJob()
        val timeoutJob = createTimeoutJob(timeout.seconds, context)
        pluginRun(block, uiStreamDebug, agentStreamDebug, context)
        timeoutJob.cancel()
    }

}

fun AdminTest.addPlugin(
    agentId: String,
    payload: PluginId,
    token: String = globToken,
    resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
) = callAsync(asyncEngine.context) {
    engine.handleRequest(
        HttpMethod.Post,
        "/api" + engine.application.locations.href(Routes.Api.Agent.AddNewPlugin(agentId))
    ) {
        addHeader(HttpHeaders.Authorization, "Bearer $token")
        setBody(PluginId.serializer() stringify payload)
    }.apply { resultBlock(response.status(), response.content) }
}

fun AdminTest.unRegister(
    agentId: String,
    token: String = globToken,
    resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
) =
    callAsync(asyncEngine.context) {
        engine.handleRequest(
            HttpMethod.Post,
            "/api" + engine.application.locations.href(Routes.Api.Agent.UnregisterAgent(agentId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
        }.apply { resultBlock(response.status(), response.content) }
    }

fun AdminTest.unLoadPlugin(
    agentId: String,
    payload: PluginId,
    token: String = globToken,
    resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
) {
    callAsync(asyncEngine.context) {
        engine.handleRequest(
            HttpMethod.Post,
            "/api" + engine.application.locations.href(Routes.Api.Agent.UnloadPlugin(agentId, payload.pluginId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
        }.apply { resultBlock(response.status(), response.content) }
    }
}

fun AdminTest.togglePlugin(
    agentId: String,
    pluginId: PluginId,
    token: String = globToken,
    resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
) {
    callAsync(asyncEngine.context) {
        engine.handleRequest(
            HttpMethod.Post,
            "/api" + engine.application.locations.href(Routes.Api.Agent.TogglePlugin(agentId, pluginId.pluginId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
        }.apply { resultBlock(response.status(), response.content) }
    }
}

fun AdminTest.toggleAgent(
    agentId: String,
    token: String = globToken,
    resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
) {
    callAsync(asyncEngine.context) {
        engine.handleRequest(
            HttpMethod.Post,
            "/api" + engine.application.locations.href(Routes.Api.Agent.AgentToggleStandby(agentId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
        }.apply { resultBlock(response.status(), response.content) }
    }
}

fun AdminTest.pluginAction(
    payload: String,
    serviceGroupId: String,
    pluginId: String = testPlugin.pluginId,
    token: String = globToken,
    resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }

) = callAsync(asyncEngine.context) {
    val plugin = Routes.Api.ServiceGroup.Plugin(Routes.Api.ServiceGroup(serviceGroupId), pluginId)
    engine.handleRequest(
        HttpMethod.Post,
        engine.application.locations.href(Routes.Api.ServiceGroup.Plugin.DispatchAction(plugin))
    ) {
        addHeader(HttpHeaders.Authorization, "Bearer $token")
        setBody(payload)
    }.apply { resultBlock(response.status(), response.content) }
}

data class PluginTestContext(
    val agentId: String,
    val pluginId: String,
    val buildVersionHash: String,
    val token: String,
    val classesCount: Int,
    val engine: TestApplicationEngine,
    val context: CoroutineContext,
    var lis: MutableList<Class<*>> = mutableListOf()
) {

    fun pluginAction(
        payload: String,
        pluginId: String = this.pluginId,
        agentId: String = this.agentId,
        token: String = this.token,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
    ) = callAsync(context) {
        engine.handleRequest(
            HttpMethod.Post,
            "/api" + engine.application.locations.href(Routes.Api.Agent.DispatchPluginAction(agentId, pluginId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
            setBody(payload)
        }.apply { resultBlock(response.status(), response.content) }
    }

    fun changePackages(
        agentId: String = this.agentId,
        token: String = this.token,
        payload: PackagesPrefixes = PackagesPrefixes(),
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
    ) = callAsync(context) {
        engine.handleRequest(
            HttpMethod.Post,
            "/api" + engine.application.locations.href(Routes.Api.Agent.SystemSettings(agentId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
            setBody(PackagesPrefixes.serializer() stringify payload)
        }.apply { resultBlock(response.status(), response.content) }
    }
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
