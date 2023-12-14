package com.epam.drill.admin.auth.service.impl

import com.epam.drill.admin.auth.service.ApiKey
import com.epam.drill.admin.auth.service.ApiKeyBuilder

class ApiKeyBuilderImpl: ApiKeyBuilder {
    override fun format(apiKey: ApiKey): String {
        return "${apiKey.identifier}_${apiKey.secret}"
    }

    override fun parse(apiKey: String): ApiKey {
        return apiKey.split("_").let { ApiKey(it.first().toInt(), it.last()) }
    }
}