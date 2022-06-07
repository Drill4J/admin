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

import com.epam.drill.analytics.item.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import mu.*
import java.io.*
import java.nio.file.*
import java.util.*
import kotlin.time.*

fun isAnalyticDisabled() = System.getProperty(AnalyticService.ANALYTIC_DISABLE).toBoolean()

object AnalyticService : Closeable {

    private val logger = KotlinLogging.logger { AnalyticService::class.simpleName }

    const val ANALYTIC_DISABLE = "analytic.disable"
    val CLIENT_ID = getClientId()

    private const val CLIENT_ID_PROPERTY = "client.id"
    private val SCHEDULE_SEND_DELAY = Duration.days(1)
    private val scheduleEventQueue = Channel<StatisticsEvent>()
    private val scheduleJob: Job = GlobalScope.launch {
        while (true) {
            delay(SCHEDULE_SEND_DELAY)
            while (!scheduleEventQueue.isEmpty && !scheduleEventQueue.isClosedForReceive) {
                sendEvent(scheduleEventQueue.receive())
            }
        }
    }

    private val drillAnalyticClient = AnalyticClient("UA-214931987-3", CLIENT_ID)

    private val analyticClient: AnalyticApiClient
        get() = if (isAnalyticDisabled()) StubClient else drillAnalyticClient

    init {
        if (isAnalyticDisabled()) {
            logger.info { "Analytics disabled" }
        } else {
            logger.info { "Analytics enabled" }
        }
    }

    suspend fun sendEvent(event: StatisticsEvent) {
        analyticClient.send(event.payload)
    }

    suspend fun sendOnSchedule(event: StatisticsEvent) = scheduleEventQueue.send(event)

    private fun getClientId(): String {
        val localDataStorage = Paths.get(System.getProperty("user.home"), ".drill", "drill.properties")
        val properties = Properties()
        if (Files.exists(localDataStorage)) {
            runCatching {
                properties.load(Files.newInputStream(localDataStorage, StandardOpenOption.READ))
                properties.getProperty(CLIENT_ID_PROPERTY)?.let { return it }
            }
        }
        val id = UUID.randomUUID().toString()
        properties.setProperty(CLIENT_ID_PROPERTY, id)
        runCatching {
            Files.createDirectories(localDataStorage.parent)
            properties.store(Files.newOutputStream(localDataStorage, StandardOpenOption.CREATE), null)
        }
        return id
    }

    override fun close() {
        scheduleEventQueue.close()
        analyticClient.close()
        scheduleJob.cancel()
    }
}
