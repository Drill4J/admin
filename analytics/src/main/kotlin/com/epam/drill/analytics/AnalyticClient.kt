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
package com.epam.drill.analytics

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.collections.immutable.*
import mu.*
import java.io.*


typealias StatisticsItems = Map<String, String>

interface AnalyticApiClient : Closeable {
    suspend fun send(items: StatisticsItems)
}

internal class AnalyticClient(trackingId: String, clientId: String) : AnalyticApiClient {

    private val logger = KotlinLogging.logger { AnalyticService::class.simpleName }

    /**
     * Google analytic api could be found here
     * see [https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters]
     */
    private val requiredParams = persistentHashMapOf(
        "v" to "1",
        "de" to "UTF-8",
        "tid" to trackingId,
        "cid" to clientId
    )

    private val client = HttpClient(CIO)

    override suspend fun send(items: StatisticsItems) {
        runCatching {
            val url = googleAnalyticsUrl()
            val payload = (requiredParams + items).toStatisticsPayload()
            logger.trace { "Trying to send statistics to $url  with payload '$payload'" }
            val httpResponse: HttpResponse = client.post(url) {
                body = payload
            }
            logger.trace { "Statistics was sent. Response status ${httpResponse.status}" }
        }
    }

    private fun googleAnalyticsUrl(configureParams: ParametersBuilder.() -> Unit = {}) = URLBuilder(
        protocol = URLProtocol.HTTPS,
        host = "www.google-analytics.com",
        encodedPath = "/collect",
        parameters = ParametersBuilder().apply(configureParams)
    ).build()

    private fun StringValuesBuilder.addAll(
        params: Map<String, String>,
    ) = params.entries.forEach { (k, v) -> append(k, v) }

    private fun StatisticsItems.toStatisticsPayload(): String = ParametersBuilder().also {
        it.addAll(this)
    }.build().formUrlEncode()

    override fun close() {
        client.close()
    }
}

class StubClient : AnalyticApiClient {
    override suspend fun send(items: StatisticsItems) {
        // do noting
    }

    override fun close() {
        // do noting
    }
}

