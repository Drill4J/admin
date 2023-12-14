package com.epam.drill.admin.auth.service

interface ApiKeyBuilder {
    fun format(apiKey: ApiKey): String
    fun parse(apiKey: String): ApiKey
}

data class ApiKey(val identifier: Int, val secret: String)