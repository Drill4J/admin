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
package com.epam.drill.admin.writer.rawdata.queue.record

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
import kotlin.reflect.KClass

enum class RecordKey(val value: String, val payloadType: KClass<out RawDataPayload>) {
    COVERAGE("coverage", CoveragePayload::class),
    BUILDS_INFO("builds-info", BuildInfoPayload::class),
    BUILDS("builds", BuildPayload::class),
    INSTANCES("instances", InstancePayload::class),
    METHODS("methods", MethodsPayload::class),
    TEST_DEFINITIONS("test-definitions", AddTestDefinitionsPayload::class),
    TEST_LAUNCHES("test-launches", AddTestLaunchesPayload::class),
    TEST_METADATA("test-metadata", AddTestsPayload::class),
    TEST_SESSIONS("test-sessions", SessionPayload::class);

    companion object {
        private val map = RecordKey.entries.associateBy(RecordKey::value)
        fun fromValue(value: String): RecordKey = map[value] ?: throw IllegalArgumentException("Unknown RecordKey value: $value")
    }
}