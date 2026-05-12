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

import com.epam.drill.admin.writer.rawdata.route.payload.RawDataPayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

class JsonDeserializer<out T>(
    private val serializer: KSerializer<T>,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) {
    fun deserialize(bytes: ByteArray): T {
        val decoded = bytes.toString(Charsets.UTF_8)
        return json.decodeFromString(serializer, decoded)
    }
}

fun <T: RawDataPayload> json(type: KClass<out T>, bytes: ByteArray): T = JsonDeserializer(type.serializer()).deserialize(bytes)
