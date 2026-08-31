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
package com.epam.drill.admin.etl.impl

import com.epam.drill.admin.etl.EtlContext
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

enum class NamingConvention {
    CAMELCASE,
    UNDERSCORE
}

private fun camelToUnderscore(name: String): String =
    name.replace(Regex("([a-z])([A-Z])")) { match ->
        "${match.groupValues[1]}_${match.groupValues[2].lowercase()}"
    }

fun EtlContext.toMap(namingConvention: NamingConvention = NamingConvention.CAMELCASE): Map<String, Any?> {
    val kClass = this::class
    return kClass.memberProperties.associate { prop ->
        val rawName = prop.name
        val key = when (namingConvention) {
            NamingConvention.CAMELCASE -> rawName
            NamingConvention.UNDERSCORE -> camelToUnderscore(rawName)
        }
        key to prop.getter.call(this)
    }
}

fun Map<String, Any?>.toEtlContext(namingConvention: NamingConvention = NamingConvention.CAMELCASE): EtlContext {
    val kClass = EtlContext::class
    val constructor = kClass.primaryConstructor
        ?: error("Class ${kClass.simpleName} has no primary constructor")

    val args = constructor.parameters.associateWith { param ->
        val key = when (namingConvention) {
            NamingConvention.CAMELCASE -> param.name
            NamingConvention.UNDERSCORE -> param.name?.let { camelToUnderscore(it) }
        }
        this[key]
    }
    return constructor.callBy(args)
}