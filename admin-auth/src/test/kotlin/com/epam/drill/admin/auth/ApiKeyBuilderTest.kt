package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.service.ApiKey
import com.epam.drill.admin.auth.service.impl.ApiKeyBuilderImpl
import kotlin.test.*

class ApiKeyBuilderTest {

    @Test
    fun `given ApiKey object, format must return string`() {
        val apiKeyBuilder = ApiKeyBuilderImpl()
        val apiKey = ApiKey(100, "secret")

        val result = apiKeyBuilder.format(apiKey)

        assertEquals("100_secret", result)
    }

    @Test
    fun `given api key string, parse must return ApiKey object`() {
        val apiKeyBuilder = ApiKeyBuilderImpl()

        val apiKey = apiKeyBuilder.parse("100_secret")

        assertEquals(100, apiKey.identifier)
        assertEquals("secret", apiKey.secret)
    }

}