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
package com.epam.drill.admin.store

import com.epam.drill.admin.*
import com.epam.dsm.*
import com.zaxxer.hikari.*
import kotlinx.coroutines.sync.*
import mu.*
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * Lazy initialization of the datasource clients for a plugin
 *
 * @param pluginId the plugin ID
 * @param config the DB configuration
 * @return the datasource client
 */
suspend fun pluginStoresDSM(pluginId: String, config: HikariConfig = hikariConfig): StoreClient =
    storeClientPlugins[pluginId] ?: createOncePluginStore(pluginId, config)

val storeClientPlugins = mutableMapOf<String, StoreClient>()

private val mutex = Mutex()

private suspend fun createOncePluginStore(pluginId: String, config: HikariConfig): StoreClient {
    mutex.withLock {
        val storeClient = storeClientPlugins[pluginId]
        if (storeClient != null) {
            logger.trace { "get store client $pluginId" }
            return storeClient
        }
        logger.info { "create a new store client $pluginId" }
        val newStoreClient = StoreClient(config.copyConfig(pluginSchemaName(pluginId)))
        storeClientPlugins[pluginId] = newStoreClient
        return newStoreClient
    }
}

fun pluginSchemaName(pluginId: String) =
    "plugins_${pluginId.lowercase(Locale.getDefault()).replace('-', '_')}"

fun HikariConfig.copyConfig(schema: String): HikariConfig {
    logger.debug { "copy config with $schema" }
    val hikariConfig = HikariConfig()
    this.copyStateTo(hikariConfig)
    hikariConfig.schema = schema
    return hikariConfig
}

val adminStore = StoreClient(hikariConfig.copyConfig("admin"))
