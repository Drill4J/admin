package com.epam.drill.admin.kodein

import com.epam.drill.admin.*
import com.epam.drill.admin.agent.*
import com.epam.drill.admin.agent.logging.*
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.impl.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.admin.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.endpoints.system.*
import com.epam.drill.admin.notification.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugin.PluginSessions
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.service.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.store.*
import com.epam.drill.admin.version.*
import com.epam.drill.admin.websocket.*
import com.epam.kodux.*
import io.ktor.application.*
import org.kodein.di.*
import org.kodein.di.generic.*

val pluginServices: Kodein.Builder.(Application) -> Unit
    get() = { application ->
        bind<PluginLoaderService>() with eagerSingleton { PluginLoaderService(application) }
        bind<Plugins>() with singleton { instance<PluginLoaderService>().plugins }
        bind<PluginStores>() with eagerSingleton {
            PluginStores(drillWorkDir.resolve("plugins")).also { application.closeOnStop(it) }
        }
        bind<PluginCaches>() with singleton { PluginCaches(instance(), instance(), instance()) }
        bind<PluginSessions>() with singleton { PluginSessions(instance()) }
        bind<PluginSenders>() with singleton { PluginSenders(kodein) }
    }

val storage: Kodein.Builder.(Application) -> Unit
    get() = { app ->
        bind<StoreManager>() with eagerSingleton {
            StoreManager(drillWorkDir.resolve("agents")).also { app.onStop { it.close() } }
        }
        bind<CommonStore>() with eagerSingleton { CommonStore(drillWorkDir).also { app.closeOnStop(it) } }
        bind<AgentStorage>() with singleton { AgentStorage() }
        bind<CacheService>() with eagerSingleton { JvmCacheService() }
        bind<ServiceGroupManager>() with eagerSingleton { ServiceGroupManager(kodein) }
        bind<AgentManager>() with eagerSingleton { AgentManager(kodein) }
        bind<SessionStorage>() with eagerSingleton { SessionStorage() }
        bind<AgentDataCache>() with eagerSingleton { AgentDataCache() }
        bind<NotificationManager>() with eagerSingleton { NotificationManager(kodein) }
        bind<LoggingHandler>() with eagerSingleton { LoggingHandler(kodein) }
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
        bind<PluginDispatcher>() with eagerSingleton { PluginDispatcher(kodein) }
        bind<InfoController>() with eagerSingleton { InfoController(kodein) }
        bind<LoginEndpoint>() with eagerSingleton { LoginEndpoint(instance()) }
        bind<VersionEndpoints>() with eagerSingleton { VersionEndpoints(kodein) }
        bind<ServiceGroupHandler>() with eagerSingleton { ServiceGroupHandler(kodein) }
        bind<AgentHandler>() with eagerSingleton { AgentHandler(kodein) }
        bind<NotificationEndpoints>() with eagerSingleton { NotificationEndpoints(kodein) }
        bind<RequestValidator>() with eagerSingleton { RequestValidator(kodein) }
    }
