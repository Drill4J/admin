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
package com.epam.drill.admin.writer.rawdata.queue.impl

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
import com.epam.drill.admin.writer.rawdata.route.jsonConfig
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

class JsonDeserializer<out T>(
    private val serializer: KSerializer<T>,
    private val json: Json = jsonConfig
) {
    fun deserialize(bytes: ByteArray): T {
        val decoded = bytes.toString(Charsets.UTF_8)
        return json.decodeFromString(serializer, decoded)
    }
}

fun <T : RawDataPayload> ByteArray.deserializeJson(type: KClass<out T>): T =
    JsonDeserializer(type.serializer()).deserialize(this)

fun DataIngestRoute.toPayloadType(): KClass<out RawDataPayload> {
    return when (this) {
        is CoverageRoute -> CoveragePayload::class
        is BuildsInfoRoute -> BuildInfoPayload::class
        is BuildsRoute -> BuildPayload::class
        is InstancesRoute -> InstancePayload::class
        is MethodsRoute -> MethodsPayload::class
        is TestDefinitionsRoute -> AddTestDefinitionsPayload::class
        is TestLaunchesRoute -> AddTestLaunchesPayload::class
        is TestMetadataRoute -> AddTestsPayload::class
        is TestSessionRoute -> SessionPayload::class
    }
}
