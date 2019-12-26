package com.epam.drill.e2e

import com.epam.drill.admin.*
import com.epam.drill.admin.store.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.jwt.config.*
import com.epam.drill.admin.kodein.*
import com.epam.drill.admin.plugins.*
import com.epam.kodux.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.serialization.*
import io.ktor.websocket.*
import org.kodein.di.generic.*
import java.io.*
import java.util.*

class AppConfig(var projectDir: File) {
    lateinit var wsTopic: WsTopic
    lateinit var storeManager: StoreManager
    lateinit var commonStore: CommonStore


    val testApp: Application.(String, Boolean) -> Unit = { sslPort, withArtifactory ->
        (environment.config as MapApplicationConfig).apply {
            put("ktor.deployment.sslPort", sslPort)
            put("ktor.dev", "true")
        }
        install(Locations)
        install(WebSockets)
        install(Authentication) {
            jwt {
                realm = "Drill4J app"
                verifier(JwtAuth.verifier(TokenType.Access))
                validate {
                    it.payload.getClaim("id").asInt()?.let(userSource::findUserById)
                }
            }
        }

        install(ContentNegotiation) {
            register(ContentType.Any, EmptyContentWrapper())
            serialization()
        }

        enableSwaggerSupport()

        kodeinApplication(AppBuilder {

            withKModule { kodeinModule("storage", storage) }
            withKModule { kodeinModule("wsHandler", wsHandler) }
            withKModule { kodeinModule("handlers", handlers) }
            withKModule {
                kodeinModule("pluginServices") {
                    bind<Plugins>() with singleton { Plugins() }
                    bind<PluginLoaderService>() with eagerSingleton {
                        PluginLoaderService(
                            kodein,
                            projectDir.resolve("work"),
                            withArtifactory
                        )
                    }
                }
            }

            val baseLocation = projectDir.resolve(UUID.randomUUID().toString())

            withKModule {
                kodeinModule("addition") {
                    bind<StoreManager>() with eagerSingleton {
                        storeManager = StoreManager(baseLocation.resolve("agent"))
                        storeManager
                    }
                    bind<CommonStore>() with eagerSingleton {
                        commonStore = CommonStore(baseLocation.resolve("common"))
                        commonStore
                    }
                    bind<WsTopic>() with singleton {
                        wsTopic = WsTopic(kodein)
                        wsTopic
                    }
                }
            }
        })
    }
}
