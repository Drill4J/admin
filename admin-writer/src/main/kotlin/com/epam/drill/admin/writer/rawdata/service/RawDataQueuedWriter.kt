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
import com.epam.drill.admin.writer.rawdata.route.payload.TestDefinitionPayload
import mu.KotlinLogging
import kotlin.reflect.KClass

class RawDataQueuedWriter(
    handler: RawDataWriter,
    workers: Int = 10,
    private val queue: DataQueue<RawDataPayload>
) {
    private val logger = KotlinLogging.logger {}
    private val queueProcessor = QueueProcessor<RawDataPayload>(
        handler = { payload ->
            when (payload) {
                is CoveragePayload -> handler.saveCoverage(payload)
                is MethodsPayload -> handler.saveMethods(payload)
                is BuildPayload -> handler.saveBuild(payload)
                is AddTestDefinitionsPayload -> handler.saveTestDefinitions(payload)
                is AddTestLaunchesPayload -> handler.saveTestLaunches(payload)
                is AddTestsPayload -> handler.saveTestMetadata(payload)
                is InstancePayload -> handler.saveInstance(payload)
                is SessionPayload -> handler.saveTestSession(payload, null)
            }
        },
        onError = { payload, e ->
            logger.error(e) { "Error while saving [${payload::class.simpleName}]: ${e.message}" }
        },
        onSuccess = { payload ->
            logger.debug { "Successfully saved [${payload::class.simpleName}]" }
        }
    )

    init {
        queueProcessor.run(queue, workers)
    }

    suspend fun enqueueBuild(data: ByteArray) {
        queue.enqueue(BuildPayload::class, data)
    }

    suspend fun enqueueMethods(data: ByteArray) {
        queue.enqueue(MethodsPayload::class, data)
    }

    suspend fun enqueueInstance(data: ByteArray) {
        queue.enqueue(InstancePayload::class, data)
    }

    suspend fun enqueueCoverage(data: ByteArray) {
        queue.enqueue(CoveragePayload::class, data)
    }

    suspend fun enqueueTestDefinitions(data: ByteArray) {
        queue.enqueue(AddTestDefinitionsPayload::class, data)
    }

    suspend fun enqueueTestLaunches(data: ByteArray) {
        queue.enqueue(AddTestLaunchesPayload::class, data)
    }

    suspend fun enqueueTestMetadata(data: ByteArray) {
        queue.enqueue(AddTestsPayload::class, data)
    }
}