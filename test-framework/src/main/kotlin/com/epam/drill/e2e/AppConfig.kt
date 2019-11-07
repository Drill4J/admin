package com.epam.drill.e2e

import com.epam.drill.*
import com.epam.drill.endpoints.*
import com.epam.drill.jwt.config.*
import com.epam.drill.kodein.*
import com.epam.kodux.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.config.*
import io.ktor.locations.*
import io.ktor.websocket.*
import org.kodein.di.generic.*
import java.io.*
import java.util.*

class AppConfig(var projectDir: File) {
    lateinit var wsTopic: WsTopic

    val testApp: Application.(String) -> Unit = { sslPort ->
        (environment.config as MapApplicationConfig).apply {
            put("ktor.deployment.sslPort", sslPort)
            put("ktor.dev", "true")
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
        kodeinApplication(AppBuilder {
            withKModule { kodeinModule("storage", storage) }
            withKModule { kodeinModule("wsHandler", wsHandler) }
            withKModule { kodeinModule("handlers", handlers) }
            withKModule { kodeinModule("pluginServices", pluginServices) }
            val baseLocation = projectDir.resolve(UUID.randomUUID().toString()).resolve("agent")
            withKModule {
                kodeinModule("addition") {
                    bind<StoreManger>() with eagerSingleton {
                        StoreManger(baseLocation)
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