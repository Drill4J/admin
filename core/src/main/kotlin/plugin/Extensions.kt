package com.epam.drill.admin.plugin

import com.epam.drill.plugin.api.end.*
import kotlinx.serialization.*
import kotlinx.serialization.internal.*
import kotlin.reflect.full.*


suspend fun AdminPluginPart<*>.getPluginData(type: String = ""): Any? {
    val params = if (type.isNotEmpty()) mapOf("type" to type) else emptyMap()
    val data = getPluginData(params)
    return data.takeIf { it != Unit && it != "" }
}


//TODO redesign plugin api
@UseExperimental(InternalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
fun AdminPluginPart<*>.stringifyAction(data: Any): String? {
    val serializer = serDe.actionSerializer as AbstractPolymorphicSerializer<Any>
    return if (data::class.isSubclassOf(serializer.baseClass)) {
        serializer.stringify(data)
    } else null
}
