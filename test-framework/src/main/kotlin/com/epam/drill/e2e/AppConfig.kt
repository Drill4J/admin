/**
 * Copyright 2020 - 2022 EPAM Systems
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

import com.epam.drill.admin.*
import com.epam.drill.admin.auth.route.userAuthenticationRoutes
import com.epam.drill.admin.auth.securityDiConfig
import com.epam.drill.admin.auth.usersDiConfig
import com.epam.drill.admin.config.*
import com.epam.drill.admin.di.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.kodein.*
import com.epam.drill.admin.plugin.PluginCaches
import com.epam.drill.admin.plugin.PluginMetadata
import com.epam.drill.admin.plugin.PluginSenders
import com.epam.drill.admin.plugin.PluginSessions
import com.epam.drill.admin.plugins.Plugin
import com.epam.drill.admin.plugins.Plugins
import com.epam.drill.admin.store.*
import com.epam.dsm.*
import com.epam.dsm.test.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.websocket.*
import org.kodein.di.*
import java.io.*
import com.epam.drill.admin.plugins.coverage.TestAdminPart
import com.epam.drill.admin.plugins.test2CodePlugin
import io.ktor.routing.*

const val GUEST_USER = "{\"username\": \"guest\", \"password\": \"guest\", \"role\": \"ADMIN\"}"

class AppConfig(var projectDir: File, delayBeforeClearData: Long, useTest2CodePlugin: Boolean = false) {
    lateinit var wsTopic: WsTopic
    lateinit var storeManager: StoreClient

    val testApp: Application.(String) -> Unit = { sslPort ->
        (environment.config as MapApplicationConfig).apply {
            put("ktor.deployment.sslPort", sslPort)
            put("drill.devMode", "true")
            put("drill.plugins.remote.enabled", "false")
            put("drill.agents.socket.timeout", "90")
            put("drill.cache.type", "jvm")
            put("drill.users", listOf(GUEST_USER))
        }
        install(Locations)
        install(WebSockets)

        install(ContentNegotiation) {
            converters()
        }

        enableSwaggerSupport()

        kodein {
            withKModule { kodeinModule("securityConfig", securityDiConfig) }
            withKModule { kodeinModule("usersConfig", usersDiConfig) }
            withKModule { kodeinModule("pluginServices", testPluginServices(useTest2CodePlugin)) }
            withKModule { kodeinModule("storage", storage) }
            withKModule { kodeinModule("wsHandler", wsHandler) }
            withKModule { kodeinModule("handlers", handlers) }

            withKModule {
                kodeinModule("addition") {
                    hikariConfig = TestDatabaseContainer.createDataSource()
                    storeManager = StoreClient(hikariConfig.copyConfig("admin_test"))
                    bind<WsTopic>() with singleton {
                        wsTopic = WsTopic(di)
                        wsTopic
                    }
                }
            }
        }

        routing {
            userAuthenticationRoutes()
        }

        environment.monitor.subscribe(ApplicationStopped) {
            println("test app stopping...")
            Thread.sleep(delayBeforeClearData)//for parallel tests, example: MultipleAgentRegistrationTest
            println("after sleep, clearing data...")
            projectDir.deleteRecursively()
            TestDatabaseContainer.clearData()
            storeManager.close()
        }
    }
}

fun testPluginServices(useTest2CodePlugin: Boolean = false): DI.Builder.(Application) -> Unit = { application ->
    if (useTest2CodePlugin)
        bind<Plugins>() with singleton { Plugins(mapOf("test2code" to test2CodePlugin())) }
    else
        bind<Plugins>() with singleton { Plugins(mapOf("test2code" to testPlugin())) }
    bind<PluginCaches>() with singleton {
        PluginCaches(
            application,
            instance(),
            instance()
        )
    }
    bind<PluginSessions>() with singleton { PluginSessions(instance()) }
    bind<PluginSenders>() with singleton { PluginSenders(di) }
}

private fun testPlugin(): Plugin {
    val pluginId = "test2code"
    return Plugin(
        pluginClass = TestAdminPart::class.java,
        pluginBean = PluginMetadata(
            id = pluginId,
            name = "Test plugin",
            description = "This is the test plugin",
            type = "Custom",
            config = "{\"message\": \"temp message\"}"
        ),
        version = "version"
    )
}
