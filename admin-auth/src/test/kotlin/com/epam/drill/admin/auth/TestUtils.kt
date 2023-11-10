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

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.epam.drill.admin.auth.model.DataResponse
import com.epam.drill.admin.auth.principal.Role
import io.ktor.application.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.mockito.kotlin.whenever
import org.mockito.stubbing.OngoingStubbing
import java.net.URLDecoder
import java.util.*
import kotlin.test.assertNotNull


fun TestApplicationRequest.addBasicAuth(username: String, password: String) {
    val encodedCredentials = String(Base64.getEncoder().encode("$username:$password".toByteArray()))
    addHeader(HttpHeaders.Authorization, "Basic $encodedCredentials")
}

fun TestApplicationRequest.addJwtToken(
    username: String,
    secret: String = "secret",
    expiresAt: Date = Date(System.currentTimeMillis() + 10_000),
    role: String = Role.UNDEFINED.name,
    issuer: String? = "issuer",
    audience: String? = null,
    algorithm: Algorithm = Algorithm.HMAC512(secret),
    configure: JWTCreator.Builder.() -> Unit = { withClaim("role", role) }
) {
    val token = JWT.create()
        .withSubject(username)
        .withIssuer(issuer)
        .withAudience(audience)
        .withExpiresAt(expiresAt)
        .apply(configure)
        .sign(algorithm)
    addHeader(HttpHeaders.Authorization, "Bearer $token")
}

fun <T> TestApplicationCall.assertResponseNotNull(serializer: KSerializer<T>): T {
    val value = assertNotNull(response.content)
    val response = Json.decodeFromString(DataResponse.serializer(serializer), value)
    return response.data
}

fun Application.environment(configuration: MapApplicationConfig.() -> Unit) {
    (this.environment.config as MapApplicationConfig).apply {
        configuration()
    }
}

fun <M, T> wheneverBlocking(mock: M, methodCall: suspend M.() -> T): OngoingStubbing<T> {
    return runBlocking { whenever(mock.methodCall()) }
}

suspend fun HttpRequestData.formData(): Map<String, String> {
    val parameters = String(this.body.toByteArray())
    val parameterPairs = parameters.split("&")
    val parameterMap = mutableMapOf<String, String>()

    for (pair in parameterPairs) {
        val (key, value) = pair.split("=")
        parameterMap[key] = withContext(Dispatchers.IO) {
            URLDecoder.decode(value, "UTF-8")
        }
    }

    return parameterMap
}
