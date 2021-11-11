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
package com.epam.drill.admin.kodein

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
import org.kodein.di.generic.*

val pluginServices: Kodein.Builder.(Application) -> Unit
    get() = { application ->
        bind<PluginLoaderService>() with eagerSingleton { PluginLoaderService(application) }
        bind<Plugins>() with singleton { instance<PluginLoaderService>().plugins }
        bind<PluginCaches>() with singleton { PluginCaches(application, instance(), instance()) }
        bind<PluginSessions>() with singleton { PluginSessions(instance()) }
        bind<PluginSenders>() with singleton { PluginSenders(kodein) }
    }

val storage: Kodein.Builder.(Application) -> Unit
    get() = { app ->
        bind<AgentStorage>() with singleton { AgentStorage() }
        if (app.drillCacheType == "mapdb") {
            bind<CacheService>() with eagerSingleton { MapDBCacheService() }
        } else bind<CacheService>() with eagerSingleton { JvmCacheService() }
        bind<GroupManager>() with eagerSingleton { GroupManager(kodein) }
        bind<AgentManager>() with eagerSingleton { AgentManager(kodein) }
        bind<SessionStorage>() with eagerSingleton { SessionStorage() }
        bind<AgentDataCache>() with eagerSingleton { AgentDataCache() }
        bind<NotificationManager>() with eagerSingleton { NotificationManager(kodein) }
        bind<LoggingHandler>() with eagerSingleton { LoggingHandler(kodein) }
        bind<ConfigHandler>() with eagerSingleton { ConfigHandler(kodein) }
    }

val wsHandler: Kodein.Builder.(Application) -> Unit
    get() = { _ ->
        bind<AgentEndpoints>() with eagerSingleton {
            AgentEndpoints(
                kodein
            )
        }
        bind<DrillPluginWs>() with eagerSingleton { DrillPluginWs(kodein) }
        bind<DrillServerWs>() with eagerSingleton {
            DrillServerWs(
                kodein
            )
        }
        bind<TopicResolver>() with eagerSingleton {
            TopicResolver(
                kodein
            )
        }
        bind<ServerWsTopics>() with eagerSingleton {
            ServerWsTopics(
                kodein
            )
        }
        bind<WsTopic>() with singleton { WsTopic(kodein) }
    }

val handlers: Kodein.Builder.(Application) -> Unit
    get() = { _ ->
        bind<DrillAdminEndpoints>() with eagerSingleton {
            DrillAdminEndpoints(
                kodein
            )
        }
        bind<LocationRouteService>() with eagerSingleton { LocationAttributeRouteService() }
        bind<PluginDispatcher>() with eagerSingleton { PluginDispatcher(kodein) }
        bind<LoginEndpoint>() with eagerSingleton { LoginEndpoint(instance()) }
        bind<VersionEndpoints>() with eagerSingleton { VersionEndpoints(kodein) }
        bind<GroupHandler>() with eagerSingleton { GroupHandler(kodein) }
        bind<AgentHandler>() with eagerSingleton { AgentHandler(kodein) }
        bind<NotificationEndpoints>() with eagerSingleton { NotificationEndpoints(kodein) }
        bind<RequestValidator>() with eagerSingleton { RequestValidator(kodein) }
    }
