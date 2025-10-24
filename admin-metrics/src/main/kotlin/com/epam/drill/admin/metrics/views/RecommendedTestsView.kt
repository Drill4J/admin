package com.epam.drill.admin.metrics.views

import kotlinx.serialization.Serializable

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
@Serializable
class RecommendedTestsView(
    val testDefinitionId: String,
    val testRunner: String? = null,
    val testPath: String,
    val testName: String,
    val tags: List<String>? = null,
    val metadata: Map<String, String>? = null,
    val testImpactStatus: TestImpactStatus? = null,
    val impactedMethods: Int? = null,
    val baselineBuildId: String? = null,
)