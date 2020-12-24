package com.epam.drill.e2e

import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.group.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.store.*
import com.epam.drill.e2e.plugin.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.junit.jupiter.api.fail
import java.io.*
import java.util.*
import kotlin.time.*
import kotlin.time.TimeSource.*

abstract class AdminTest {
    var watcher: (suspend AsyncTestAppEngine.(Channel<GroupedAgentsDto>) -> Unit?)? = null
    val projectDir = File("build/tmp/test/${this::class.simpleName}-${UUID.randomUUID()}")

    lateinit var asyncEngine: AsyncTestAppEngine
    val engine: TestApplicationEngine get() = asyncEngine.engine
    lateinit var globToken: String
    lateinit var storeManager: AgentStores
    lateinit var commonStore: CommonStore

    internal val testAgentContext = TestAgentContext()

    fun uiWatcher(bl: suspend AsyncTestAppEngine.(Channel<GroupedAgentsDto>) -> Unit): AdminTest {
        this.watcher = bl
        return this
    }

    fun AsyncTestAppEngine.register(
        agentId: String,
        token: String = globToken,
        payload: AgentRegistrationDto = AgentRegistrationDto(
            name = "xz",
            description = "ad",
            systemSettings = SystemSettingsDto(
                packages = listOf("testPrefix")
            ),
            plugins = emptyList()
        ),
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
    ) = callAsync(context) {
        with(engine) {
            handleRequest(
                HttpMethod.Patch,
                toApiUri(agentApi { ApiRoot.Agents.Agent(it, agentId) })
            ) {
                addHeader(HttpHeaders.Authorization, "Bearer $token")
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(AgentRegistrationDto.serializer() stringify payload)
            }.apply { resultBlock(response.status(), response.content) }
        }
    }
}

fun TestApplicationEngine.toApiUri(location: Any): String = application.locations.href(location).let { uri ->
    if (uri.startsWith("/api")) uri else "/api$uri"
}

@ExperimentalTime
fun CoroutineScope.createTimeoutJob(timeout: Duration, context: Job) = launch {
    val expirationMark = Monotonic.markNow() + timeout
    while (true) {
        delay(50)
        if (expirationMark.hasPassedNow()) {
            context.cancelChildren()
            fail("Timeout exception")
        }
    }
}
