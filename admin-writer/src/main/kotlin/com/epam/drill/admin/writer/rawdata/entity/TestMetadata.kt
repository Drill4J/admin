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
package com.epam.drill.admin.writer.rawdata.entity

import java.time.LocalDateTime

class TestMetadata (
    val launch: TestLaunch,
    val definition: TestDefinition
)

class TestLaunch (
    val groupId: String,
    val id: String,
    val testDefinitionId: String,
    val testSessionId: String,
    val result: String?
)

class TestDefinition(
    val groupId: String,
    val id: String,
    val type: String?,
    val runner: String?,
    val name: String?,
    val path: String?,
    val tags: List<String>?,
    val metadata: Map<String, String>?
)

class TestSession (
    val id: String,
    val groupId: String,
    val testTaskId: String,
    val startedAt: LocalDateTime,
)
