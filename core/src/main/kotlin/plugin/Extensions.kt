package com.epam.drill.admin.plugin

import com.epam.drill.plugin.api.end.*


suspend fun AdminPluginPart<*>.getPluginData(type: String = "") : Any? {
    val params = if (type.isNotEmpty()) mapOf("type" to type) else emptyMap()
    val data = getPluginData(params)
    return data.takeIf { it != Unit && it != "" }
}
