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
