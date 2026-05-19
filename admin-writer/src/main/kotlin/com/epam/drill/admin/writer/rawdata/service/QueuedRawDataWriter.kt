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
package com.epam.drill.admin.writer.rawdata.service

import com.epam.drill.admin.common.config.recordSuspend
import com.epam.drill.admin.writer.rawdata.config.RawDataMeter
import com.epam.drill.admin.writer.rawdata.queue.DataQueue
import com.epam.drill.admin.writer.rawdata.queue.QueueInput
import com.epam.drill.admin.writer.rawdata.queue.QueueProcessor
import com.epam.drill.admin.writer.rawdata.route.DataIngestRoute
import com.epam.drill.admin.writer.rawdata.route.payload.AddTestDefinitionsPayload
import com.epam.drill.admin.writer.rawdata.route.payload.AddTestLaunchesPayload
import com.epam.drill.admin.writer.rawdata.route.payload.AddTestsPayload
import com.epam.drill.admin.writer.rawdata.route.payload.BuildInfoPayload
import com.epam.drill.admin.writer.rawdata.route.payload.BuildPayload
import com.epam.drill.admin.writer.rawdata.route.payload.CoveragePayload
import com.epam.drill.admin.writer.rawdata.route.payload.InstancePayload
import com.epam.drill.admin.writer.rawdata.route.payload.MethodsPayload
import com.epam.drill.admin.writer.rawdata.route.payload.RawDataPayload
import com.epam.drill.admin.writer.rawdata.route.payload.SessionPayload
import mu.KotlinLogging

class QueuedRawDataWriter(
    handler: RawDataWriter,
    workers: Int = 10,
    private val queue: DataQueue<DataIngestRoute, RawDataPayload>,
    private val metrics: RawDataMeter,
) {
    private val logger = KotlinLogging.logger {}
    private val usernameKey = "username"
    private val queueProcessor = QueueProcessor<DataIngestRoute, RawDataPayload>(
        handler = { output ->
            metrics.dequeued.increment()
            metrics.savingLatency.recordSuspend {
                val payload = output.payload
                val metadata = output.metadata
                when (payload) {
                    is CoveragePayload -> handler.saveCoverage(payload)
                    is MethodsPayload -> handler.saveMethods(payload)
                    is BuildPayload -> handler.saveBuild(payload)
                    is BuildInfoPayload -> handler.saveBuildInfo(payload)
                    is AddTestDefinitionsPayload -> handler.saveTestDefinitions(payload)
                    is AddTestLaunchesPayload -> handler.saveTestLaunches(payload)
                    is AddTestsPayload -> handler.saveTestMetadata(payload)
                    is InstancePayload -> handler.saveInstance(payload)
                    is SessionPayload -> handler.saveTestSession(payload, metadata[usernameKey])
                }
            }
        },
        onError = { output, e ->
            logger.error(e) { "Error while saving [${output.payload::class.simpleName}]: ${e.message}" }
            metrics.failures.increment()
        },
        onSuccess = { payload ->
            logger.debug { "Successfully saved [${payload::class.simpleName}]" }
            metrics.saved.increment()
        }
    )

    init {
        queueProcessor.run(queue, workers)
    }

    suspend fun enqueue(route: DataIngestRoute, payload: ByteArray, username: String? = null) {
        val metadata = username?.let { mapOf(usernameKey to it) } ?: emptyMap()
        queue.enqueue(QueueInput(route, payload, metadata))
        metrics.enqueued.increment()
    }
}