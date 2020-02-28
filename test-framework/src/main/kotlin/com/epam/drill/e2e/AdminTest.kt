package com.epam.drill.e2e

import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.admin.store.*
import com.epam.drill.common.*
import com.epam.kodux.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.server.testing.*
import jetbrains.exodus.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.sync.*
import org.junit.jupiter.api.*
import java.io.*
import java.util.*
import kotlin.time.*

abstract class AdminTest {
    val mut = Mutex()
    var watcher: (suspend AsyncTestAppEngine.(Channel<GroupedAgentsDto>) -> Unit?)? = null
    val projectDir = File(System.getProperty("java.io.tmpdir") + File.separator + UUID.randomUUID())

    lateinit var asyncEngine: AsyncTestAppEngine
    val engine: TestApplicationEngine get() = asyncEngine.engine
    lateinit var globToken: String
    lateinit var storeManager: StoreManager
    lateinit var commonStore: CommonStore

    fun uiWatcher(bl: suspend AsyncTestAppEngine.(Channel<GroupedAgentsDto>) -> Unit): AdminTest {
        this.watcher = bl
        return this
    }

    @AfterEach
    fun closeResources() {
        storeManager.storages.forEach {
            try {
                it.value.close()
            } catch (ignored: ExodusException) {
            }
        }
        storeManager.storages.clear()
        try {
            commonStore.client.close()
        } catch (ignored: ExodusException) {
        } catch (ignored: UninitializedPropertyAccessException) {//FIXME get rid of this lateinit complexity
        }
    }

    fun AsyncTestAppEngine.register(
        agentId: String,
        token: String = globToken,
        payload: AgentRegistrationInfo = AgentRegistrationInfo(
            name = "xz",
            description = "ad",
            packagesPrefixes = listOf("testPrefix"),
            plugins = emptyList()
        ),
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
    ) = callAsync(context) {
        with(engine) {
            handleRequest(HttpMethod.Post, application.locations.href(Routes.Api.Agents.Agent(agentId))) {
                addHeader(HttpHeaders.Authorization, "Bearer $token")
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(AgentRegistrationInfo.serializer() stringify payload)
            }.apply { resultBlock(response.status(), response.content) }
        }
    }

}

@ExperimentalTime
fun CoroutineScope.createTimeoutJob(timeout: Duration, context: Job) = launch {
    val expirationMark = MonoClock.markNow() + timeout
    while (true) {
        delay(50)
        if (expirationMark.hasPassedNow()) {
            context.cancelChildren()
            fail("Timeout exception")
        }
    }
}
