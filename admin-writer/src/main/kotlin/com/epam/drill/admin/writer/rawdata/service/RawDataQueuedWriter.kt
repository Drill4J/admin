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

import com.epam.drill.admin.writer.rawdata.queue.DataQueue
import com.epam.drill.admin.writer.rawdata.queue.QueueInput
import com.epam.drill.admin.writer.rawdata.queue.QueueProcessor
import com.epam.drill.admin.writer.rawdata.route.payload.AddTestDefinitionsPayload
import com.epam.drill.admin.writer.rawdata.route.payload.AddTestLaunchesPayload
import com.epam.drill.admin.writer.rawdata.route.payload.AddTestsPayload
import com.epam.drill.admin.writer.rawdata.route.payload.BuildPayload
import com.epam.drill.admin.writer.rawdata.route.payload.CoveragePayload
import com.epam.drill.admin.writer.rawdata.route.payload.InstancePayload
import com.epam.drill.admin.writer.rawdata.route.payload.MethodsPayload
import com.epam.drill.admin.writer.rawdata.route.payload.RawDataPayload
import com.epam.drill.admin.writer.rawdata.route.payload.SessionPayload
import mu.KotlinLogging

const val USERNAME_KEY = "username"

class RawDataQueuedWriter(
    handler: RawDataWriter,
    workers: Int = 10,
    private val queue: DataQueue<RawDataPayload>
) {
    private val logger = KotlinLogging.logger {}
    private val queueProcessor = QueueProcessor<RawDataPayload>(
        handler = { output ->
            val payload = output.data
            val metadata = output.metadata
            when (payload) {
                is CoveragePayload -> handler.saveCoverage(payload)
                is MethodsPayload -> handler.saveMethods(payload)
                is BuildPayload -> handler.saveBuild(payload)
                is AddTestDefinitionsPayload -> handler.saveTestDefinitions(payload)
                is AddTestLaunchesPayload -> handler.saveTestLaunches(payload)
                is AddTestsPayload -> handler.saveTestMetadata(payload)
                is InstancePayload -> handler.saveInstance(payload)
                is SessionPayload -> handler.saveTestSession(payload, metadata[USERNAME_KEY])
            }
        },
        onError = { output, e ->
            logger.error(e) { "Error while saving [${output.data::class.simpleName}]: ${e.message}" }
        },
        onSuccess = { payload ->
            logger.debug { "Successfully saved [${payload::class.simpleName}]" }
        }
    )

    init {
        queueProcessor.run(queue, workers)
    }

    suspend fun enqueueBuild(data: ByteArray) {
        queue.enqueue(QueueInput(BuildPayload::class, data))
    }

    suspend fun enqueueMethods(data: ByteArray) {
        queue.enqueue(QueueInput(MethodsPayload::class, data))
    }

    suspend fun enqueueInstance(data: ByteArray) {
        queue.enqueue(QueueInput(InstancePayload::class, data))
    }

    suspend fun enqueueCoverage(data: ByteArray) {
        queue.enqueue(QueueInput(CoveragePayload::class, data))
    }

    suspend fun enqueueTestDefinitions(data: ByteArray) {
        queue.enqueue(QueueInput(AddTestDefinitionsPayload::class, data))
    }

    suspend fun enqueueTestLaunches(data: ByteArray) {
        queue.enqueue(QueueInput(AddTestLaunchesPayload::class, data))
    }

    suspend fun enqueueTestMetadata(data: ByteArray) {
        queue.enqueue(QueueInput(AddTestsPayload::class, data))
    }

    suspend fun enqueueTestSession(data: ByteArray, username: String?) {
        val metadata = username?.let { mapOf(USERNAME_KEY to username) } ?: emptyMap()
        queue.enqueue(QueueInput(SessionPayload::class, data, metadata))
    }
}