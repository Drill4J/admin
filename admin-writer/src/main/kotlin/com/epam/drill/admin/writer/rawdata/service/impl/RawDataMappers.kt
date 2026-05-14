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
package com.epam.drill.admin.writer.rawdata.service.impl

import com.epam.drill.admin.writer.rawdata.queue.record.RecordKey
import com.epam.drill.admin.writer.rawdata.route.BuildsInfoRoute
import com.epam.drill.admin.writer.rawdata.route.BuildsRoute
import com.epam.drill.admin.writer.rawdata.route.CoverageRoute
import com.epam.drill.admin.writer.rawdata.route.DataIngestRoute
import com.epam.drill.admin.writer.rawdata.route.InstancesRoute
import com.epam.drill.admin.writer.rawdata.route.MethodsRoute
import com.epam.drill.admin.writer.rawdata.route.TestDefinitionsRoute
import com.epam.drill.admin.writer.rawdata.route.TestLaunchesRoute
import com.epam.drill.admin.writer.rawdata.route.TestMetadataRoute
import com.epam.drill.admin.writer.rawdata.route.TestSessionRoute
import com.epam.drill.admin.writer.rawdata.route.payload.RawDataPayload
import kotlin.reflect.KClass

fun DataIngestRoute.toRecordKey(): RecordKey = when (this) {
    is CoverageRoute -> RecordKey.COVERAGE
    is BuildsInfoRoute -> RecordKey.BUILDS_INFO
    is BuildsRoute -> RecordKey.BUILDS
    is InstancesRoute -> RecordKey.INSTANCES
    is MethodsRoute -> RecordKey.METHODS
    is TestDefinitionsRoute -> RecordKey.TEST_DEFINITIONS
    is TestLaunchesRoute -> RecordKey.TEST_LAUNCHES
    is TestMetadataRoute -> RecordKey.TEST_METADATA
    is TestSessionRoute -> RecordKey.TEST_SESSIONS
}

fun DataIngestRoute.toPayloadType(): KClass<out RawDataPayload> = toRecordKey().payloadType

fun DataIngestRoute.toKey(): String = toRecordKey().value

fun String.toPayloadType(): KClass<out RawDataPayload> = RecordKey.fromValue(this).payloadType
