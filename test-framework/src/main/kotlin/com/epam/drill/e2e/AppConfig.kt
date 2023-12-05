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
import com.epam.drill.admin.auth.config.*
import com.epam.drill.admin.auth.route.userAuthenticationRoutes
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
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.websocket.*
import org.kodein.di.*
import java.io.*
import com.epam.drill.admin.plugins.coverage.TestAdminPart
import com.epam.drill.admin.plugins.test2CodePlugin
import io.ktor.auth.*
import io.ktor.routing.*
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di

const val GUEST_USER = "{\"username\": \"user\", \"password\": \"user\", \"role\": \"USER\"}"

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
            put("drill.auth.userRepoType", "ENV")
            put("drill.auth.envUsers", listOf(GUEST_USER))
        }
        install(Locations)
        install(WebSockets)

        install(ContentNegotiation) {
            converters()
        }

        install(RoleBasedAuthorization)

        enableSwaggerSupport()

        di {
            testPluginServices(useTest2CodePlugin)
            import(storage)
            import(wsHandler)
            import(handlers)
            import(DI.Module("addition") {
                hikariConfig = TestDatabaseContainer.createDataSource()
                storeManager = StoreClient(hikariConfig.copyConfig("admin_test"))
                bind<WsTopic>(overrides = true) with singleton {
                    wsTopic = WsTopic(di)
                    wsTopic
                }
            }, allowOverride = true)
            import(simpleAuthDIModule)
        }

        install(Authentication) {
            configureJwtAuthentication(closestDI())
            configureBasicAuthentication(closestDI())
        }

        routing {
            drillAdminRoutes()
            route("/api") {
                userAuthenticationRoutes()
            }
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

fun DI.Builder.testPluginServices(useTest2CodePlugin: Boolean = false) {
    if (useTest2CodePlugin)
        bind<Plugins>() with singleton { Plugins(mapOf("test2code" to test2CodePlugin())) }
    else
        bind<Plugins>() with singleton { Plugins(mapOf("test2code" to testPlugin())) }
    bind<PluginCaches>() with singleton {
        PluginCaches(
            instance(),
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
