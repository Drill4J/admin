package com.epam.drill.admin.kodein

import com.epam.drill.admin.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.admin.store.*
import com.epam.drill.admin.admindata.*
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.impl.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.endpoints.openapi.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.endpoints.system.*
import com.epam.drill.admin.jwt.storage.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.service.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.util.*
import com.epam.drill.admin.websockets.*
import com.epam.kodux.*
import io.ktor.application.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.*
import java.util.concurrent.*

val storage: Kodein.Builder.(Application) -> Unit
    get() = { _ ->
        bind<StoreManager>() with eagerSingleton { StoreManager(drillWorkDir.resolve("agents")) }
        bind<CommonStore>() with eagerSingleton { CommonStore(drillWorkDir) }
        bind<AgentStorage>() with singleton { ObservableMapStorage<String, AgentEntry, MutableSet<AgentWsSession>>() }
        bind<CacheService>() with eagerSingleton { JvmCacheService() }
        bind<ServiceGroupManager>() with eagerSingleton { ServiceGroupManager(kodein) }
        bind<AgentManager>() with eagerSingleton { AgentManager(kodein) }
        bind<SessionStorage>() with eagerSingleton { Collections.newSetFromMap(ConcurrentHashMap<DrillWsSession, Boolean>()) }
        bind<AdminDataVault>() with eagerSingleton { AdminDataVault() }
        bind<NotificationsManager>() with eagerSingleton { NotificationsManager(kodein) }
        bind<TokenManager>() with singleton { TokenManager(kodein) }
    }

val wsHandler: Kodein.Builder.(Application) -> Unit
    get() = { _ ->
        bind<AgentEndpoints>() with eagerSingleton { AgentEndpoints(kodein) }
        bind<Sender>() with eagerSingleton { DrillPluginWs(kodein) }
        bind<DrillServerWs>() with eagerSingleton { DrillServerWs(kodein) }
        bind<TopicResolver>() with eagerSingleton { TopicResolver(kodein) }
        bind<ServerWsTopics>() with eagerSingleton { ServerWsTopics(kodein) }
        bind<WsTopic>() with singleton { WsTopic(kodein) }
    }

val handlers: Kodein.Builder.(Application) -> Unit
    get() = { _ ->
        bind<DrillAdminEndpoints>() with eagerSingleton { DrillAdminEndpoints(kodein) }
        bind<PluginDispatcher>() with eagerSingleton { PluginDispatcher(kodein) }
        bind<InfoController>() with eagerSingleton { InfoController(kodein) }
        bind<LoginHandler>() with eagerSingleton {
            LoginHandler(
                kodein
            )
        }
        bind<ServiceGroupHandler>() with eagerSingleton { ServiceGroupHandler(kodein) }
        bind<AgentHandler>() with eagerSingleton { AgentHandler(kodein) }
        bind<RequestValidator>() with eagerSingleton { RequestValidator(kodein) }
        bind<AdminEndpointsHandler>() with eagerSingleton { AdminEndpointsHandler(kodein) }
    }

val pluginServices: Kodein.Builder.(Application) -> Unit
    get() = { _ ->
        bind<Plugins>() with singleton { Plugins() }
        bind<PluginLoaderService>() with eagerSingleton { PluginLoaderService(kodein) }
    }
