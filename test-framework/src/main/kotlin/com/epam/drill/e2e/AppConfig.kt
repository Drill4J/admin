package com.epam.drill.e2e

import com.epam.drill.admin.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.jwt.config.*
import com.epam.drill.admin.kodein.*
import com.epam.drill.admin.store.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.websocket.*
import org.kodein.di.generic.*
import java.io.*
import java.util.*

class AppConfig(var projectDir: File) {
    lateinit var wsTopic: WsTopic
    lateinit var storeManager: AgentStores
    lateinit var commonStore: CommonStore


    val testApp: Application.(String) -> Unit = { sslPort ->
        (environment.config as MapApplicationConfig).apply {
            put("ktor.deployment.sslPort", sslPort)
            put("drill.devMode", "true")
            put("drill.plugins.remote.enabled", "false")
            put("drill.agents.socket.timeout", "90")
        }
        install(Locations)
        install(WebSockets)
        install(Authentication) {
            jwt {
                realm = "Drill4J app"
                verifier(JwtConfig.verifier)
                validate {
                    it.payload.getClaim("id").asInt()?.let(userSource::findUserById)
                }
            }
        }

        install(ContentNegotiation) {
            converters()
        }

        enableSwaggerSupport()

        kodeinApplication(AppBuilder {

            withKModule { kodeinModule("pluginServices", pluginServices) }
            withKModule { kodeinModule("storage", storage) }
            withKModule { kodeinModule("wsHandler", wsHandler) }
            withKModule { kodeinModule("handlers", handlers) }

            val baseLocation = projectDir.resolve(UUID.randomUUID().toString())

            withKModule {
                kodeinModule("addition") { app ->
                    bind<CommonStore>() with eagerSingleton {
                        CommonStore(baseLocation).also {
                            app.closeOnStop(it)
                            commonStore = it
                        }
                    }
                    bind<AgentStores>() with eagerSingleton {
                        AgentStores(baseLocation).also {
                            app.closeOnStop(it)
                            storeManager = it
                        }
                    }
                    bind<PluginStores>() with eagerSingleton {
                        PluginStores(baseLocation.resolve("plugins")).also { app.closeOnStop(it) }
                    }
                    bind<WsTopic>() with singleton {
                        wsTopic = WsTopic(kodein)
                        wsTopic
                    }
                }
            }
        })
        environment.monitor.subscribe(ApplicationStopped) {
            projectDir.deleteRecursively()
        }
    }
}
