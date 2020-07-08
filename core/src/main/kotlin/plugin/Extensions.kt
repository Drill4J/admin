package com.epam.drill.admin.plugin

import com.epam.drill.plugin.api.end.*
import kotlinx.serialization.*
import kotlinx.serialization.internal.*
import kotlin.reflect.full.*

//TODO redesign plugin api
@OptIn(InternalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
fun AdminPluginPart<*>.stringifyAction(data: Any): String? {
    val serializer = serDe.actionSerializer as AbstractPolymorphicSerializer<Any>
    return if (data::class.isSubclassOf(serializer.baseClass)) {
        serializer.stringify(data)
    } else null
}
