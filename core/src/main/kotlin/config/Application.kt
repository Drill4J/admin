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
package com.epam.drill.admin.config

import com.typesafe.config.*
import io.ktor.application.*
import io.ktor.config.*
import kotlin.time.*

val Application.drillConfig: ApplicationConfig
    get() = environment.config.config("drill")

val Application.drillDefaultPackages: List<String>
    get() = drillConfig.propertyOrNull("defaultPackages")?.run {
        getString().split(",", ";", ":").map(String::trim)
    } ?: emptyList()

val Application.isDevMode: Boolean
    get() = drillConfig.propertyOrNull("devMode")?.getString()?.toBoolean() ?: false

val Application.agentSocketTimeout: Duration
    get() = drillConfig.config("agents")
        .config("socket")
        .property("timeout").getString().toInt().seconds

val Application.drillCacheType: String
    get() = drillConfig.config("cache").propertyOrNull("type")?.getString()?.toLowerCase() ?: "mapdb"

fun ApplicationConfigValue.getDuration() = "_".let { k ->
    mapOf(k to getString()).let(ConfigFactory::parseMap).getDuration(k)
}.toKotlinDuration()

val LOG_MESSAGE_MAX_LENGTH = System.getenv("LOG_MESSAGE_MAX_LENGTH")?.toIntOrNull() ?: 0
