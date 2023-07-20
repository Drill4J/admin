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
package com.epam.drill.admin.di

import com.epam.drill.admin.*
import com.epam.drill.admin.agent.*
import com.epam.drill.admin.agent.config.*
import com.epam.drill.admin.agent.logging.*
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.impl.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.admin.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.endpoints.system.*
import com.epam.drill.admin.group.*
import com.epam.drill.admin.kodein.*
import com.epam.drill.admin.notification.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.service.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.store.*
import com.epam.drill.admin.version.*
import com.epam.drill.admin.websocket.*
import io.ktor.application.*
import io.ktor.locations.*
import org.kodein.di.*


val pluginServices: DI.Builder.(Application) -> Unit
    get() = { application ->
        bind<Plugins>() with singleton { Plugins(mapOf("test2code" to test2CodePlugin())) }
        bind<PluginCaches>() with singleton { PluginCaches(application, instance(), instance()) }
        bind<PluginSessions>() with singleton { PluginSessions(instance()) }
        bind<PluginSenders>() with singleton { PluginSenders(di) }
    }

val storage: DI.Builder.(Application) -> Unit
    get() = { app ->
        bind<AgentStorage>() with singleton { AgentStorage() }
        bind<BuildStorage>() with singleton { BuildStorage() }
        if (app.drillCacheType == "mapdb") {
            bind<CacheService>() with eagerSingleton { MapDBCacheService() }
        } else bind<CacheService>() with eagerSingleton { JvmCacheService() }
        bind<GroupManager>() with eagerSingleton { GroupManager(di) }
        bind<AgentManager>() with eagerSingleton { AgentManager(di) }
        bind<BuildManager>() with eagerSingleton { BuildManager(di) }
        bind<SessionStorage>() with eagerSingleton { SessionStorage() }
        bind<AgentDataCache>() with eagerSingleton { AgentDataCache() }
        bind<NotificationManager>() with eagerSingleton { NotificationManager(di) }
        bind<LoggingHandler>() with eagerSingleton { LoggingHandler(di) }
        bind<ConfigHandler>() with eagerSingleton { ConfigHandler(di) }
    }

val wsHandler: DI.Builder.(Application) -> Unit
    get() = { _ ->
        bind<AgentEndpoints>() with eagerSingleton {
            AgentEndpoints(
                di
            )
        }
        bind<DrillPluginWs>() with eagerSingleton { DrillPluginWs(di) }
        bind<DrillServerWs>() with eagerSingleton {
            DrillServerWs(
                di
            )
        }
        bind<TopicResolver>() with eagerSingleton {
            TopicResolver(
                di
            )
        }
        bind<ServerWsTopics>() with eagerSingleton {
            ServerWsTopics(
                di
            )
        }
        bind<WsTopic>() with singleton { WsTopic(di) }
    }

val handlers: DI.Builder.(Application) -> Unit
    get() = { _ ->
        bind<DrillAdminEndpoints>() with eagerSingleton {
            DrillAdminEndpoints(
                di
            )
        }
        bind<LocationRouteService>() with eagerSingleton { LocationAttributeRouteService() }
        bind<PluginDispatcher>() with eagerSingleton { PluginDispatcher(di) }
        bind<LoginEndpoint>() with eagerSingleton { LoginEndpoint(instance()) }
        bind<VersionEndpoints>() with eagerSingleton { VersionEndpoints(di) }
        bind<GroupHandler>() with eagerSingleton { GroupHandler(di) }
        bind<AgentHandler>() with eagerSingleton { AgentHandler(di) }
        bind<NotificationEndpoints>() with eagerSingleton { NotificationEndpoints(di) }
        bind<RequestValidator>() with eagerSingleton { RequestValidator(di) }
    }
