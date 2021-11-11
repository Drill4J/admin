/**
 * Copyright 2020 EPAM Systems
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
import com.epam.drill.admin.config.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.jwt.config.*
import com.epam.drill.admin.kodein.*
import com.epam.drill.admin.store.*
import com.epam.dsm.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.websocket.*
import org.jetbrains.exposed.sql.*
import org.kodein.di.generic.*
import ru.yandex.qatools.embed.postgresql.*
import ru.yandex.qatools.embed.postgresql.distribution.*
import java.io.*
import java.util.*

class AppConfig(var projectDir: File) {
    lateinit var wsTopic: WsTopic
//    lateinit var storeManager: AgentStores
    lateinit var storeManagerDsm: StoreClient
    lateinit var commonStoreDsm: StoreClient
    lateinit var postgres: EmbeddedPostgres

    val testApp: Application.(String) -> Unit = { sslPort ->
        (environment.config as MapApplicationConfig).apply {
            put("ktor.deployment.sslPort", sslPort)
            put("drill.devMode", "true")
            put("drill.plugins.remote.enabled", "false")
            put("drill.agents.socket.timeout", "90")
            put("drill.cache.type", "jvm")
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
        println { "epta embedded postgres" }
        postgres = EmbeddedPostgres(Version.V10_6)
        val host = "localhost"
        val port = 5434
        val dbName = "dbName"
        val userName = "userName"
        val password = "password"
        postgres.start(
            host,
            port,
            dbName,
            userName,
            password
        )
        Database.connect( //todo move to API of dsm
            "jdbc:postgresql://$host:$port/$dbName", driver = "org.postgresql.Driver",
            user = userName, password = password
        ).also {
            println { "Connected to db ${it.url}" }
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

//            val baseLocation = projectDir.resolve(UUID.randomUUID().toString())

            withKModule {
                kodeinModule("addition") { app ->
                    commonStoreDsm = StoreClient("common")
                    storeManagerDsm = StoreClient("agents")
//                    bind<AgentStores>() with eagerSingleton {
//                        AgentStores(baseLocation).also {
//                            app.closeOnStop(it)
//                            storeManager = it
//                        }
//                    }
//                    bind<PluginStores>() with eagerSingleton {
//                        PluginStores(baseLocation.resolve("plugins")).also { app.closeOnStop(it) }
//                    }
                    StoreClient("plugins")
                    bind<WsTopic>() with singleton {
                        wsTopic = WsTopic(kodein)
                        wsTopic
                    }
                }
            }
        })
        environment.monitor.subscribe(ApplicationStopped) {
            com.epam.drill.admin.util.logger.info { "app stopping22..." }//todo remove
            projectDir.deleteRecursively()
            postgres.close()
        }
    }
}
