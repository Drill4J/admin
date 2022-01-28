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
import com.epam.drill.admin.config.*
import com.epam.drill.admin.di.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.jwt.config.*
import com.epam.drill.admin.kodein.*
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

class AppConfig(var projectDir: File, delayBeforeClearData: Long = 0) {
    lateinit var wsTopic: WsTopic
    lateinit var storeManager: StoreClient

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
        install(ContentNegotiation) {
            converters()
        }

        enableSwaggerSupport()

        kodeinApplication(AppBuilder {

            withKModule { kodeinModule("pluginServices", pluginServices) }
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
        })
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
