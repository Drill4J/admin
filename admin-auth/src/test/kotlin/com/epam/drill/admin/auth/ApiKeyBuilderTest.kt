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
package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.exception.NotAuthenticatedException
import com.epam.drill.admin.auth.service.ApiKey
import com.epam.drill.admin.auth.service.impl.ApiKeyBuilderImpl
import org.junit.jupiter.api.assertThrows
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

    @Test
    fun `given api key with invalid format, parse must throw NotAuthenticatedException`() {
        val apiKeyBuilder = ApiKeyBuilderImpl()
        assertThrows<NotAuthenticatedException> { apiKeyBuilder.parse("test") }
    }

    @Test
    fun `given api key with the first part, parse must throw NotAuthenticatedException`() {
        val apiKeyBuilder = ApiKeyBuilderImpl()
        assertThrows<NotAuthenticatedException> { apiKeyBuilder.parse("test_") }
    }

    @Test
    fun `given api key with the last part, parse must throw NotAuthenticatedException`() {
        val apiKeyBuilder = ApiKeyBuilderImpl()
        assertThrows<NotAuthenticatedException> { apiKeyBuilder.parse("_test") }
    }

    @Test
    fun `given api key with empty value, parse must throw NotAuthenticatedException`() {
        val apiKeyBuilder = ApiKeyBuilderImpl()
        assertThrows<NotAuthenticatedException> { apiKeyBuilder.parse("") }
    }

}