package com.epam.drill.kodein

import com.epam.drill.*
import com.epam.drill.admindata.*
import com.epam.drill.cache.*
import com.epam.drill.cache.impl.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.agent.*
import com.epam.drill.endpoints.openapi.*
import com.epam.drill.endpoints.plugin.*
import com.epam.drill.endpoints.system.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.*
import com.epam.drill.service.*
import com.epam.drill.storage.*
import com.epam.drill.util.*
import com.epam.drill.websockets.*
import com.epam.kodux.*
import io.ktor.application.*
import org.kodein.di.*
import org.kodein.di.generic.*

val storage: Kodein.Builder.(Application) -> Unit = { _ ->
    bind<StoreManger>() with eagerSingleton {StoreManger(drillWorkDir) }
    bind<AgentStorage>() with singleton { ObservableMapStorage<String, AgentEntry, MutableSet<AgentWsSession>>() }
    bind<CacheService>() with eagerSingleton { JvmCacheService() }
    bind<AgentManager>() with eagerSingleton { AgentManager(kodein) }
    bind<SessionStorage>() with eagerSingleton { HashSet<DrillWsSession>() }
    bind<AdminDataVault>() with eagerSingleton { AdminDataVault() }
    bind<NotificationsManager>() with eagerSingleton { NotificationsManager() }
}

val wsHandler: Kodein.Builder.(Application) -> Unit = { _ ->
    bind<AgentEndpoints>() with eagerSingleton { AgentEndpoints(kodein) }
    bind<Sender>() with eagerSingleton { DrillPluginWs(kodein) }
    bind<DrillServerWs>() with eagerSingleton { DrillServerWs(kodein) }
    bind<TopicResolver>() with eagerSingleton { TopicResolver(kodein) }
    bind<ServerWsTopics>() with eagerSingleton { ServerWsTopics(kodein) }
    bind<WsTopic>() with singleton { WsTopic(kodein) }
}

val handlers: Kodein.Builder.(Application) -> Unit = { _ ->
    bind<DrillAdminEndpoints>() with eagerSingleton { DrillAdminEndpoints(kodein) }
    bind<PluginDispatcher>() with eagerSingleton { PluginDispatcher(kodein) }
    bind<InfoController>() with eagerSingleton { InfoController(kodein) }
    bind<LoginHandler>() with eagerSingleton { LoginHandler(kodein) }
    bind<AgentHandler>() with eagerSingleton { AgentHandler(kodein) }
    bind<RequestValidator>() with eagerSingleton { RequestValidator(kodein) }
}

val pluginServices: Kodein.Builder.(Application) -> Unit = { _ ->
    bind<Plugins>() with singleton { Plugins() }
    bind<PluginLoaderService>() with eagerSingleton { PluginLoaderService(kodein) }
}