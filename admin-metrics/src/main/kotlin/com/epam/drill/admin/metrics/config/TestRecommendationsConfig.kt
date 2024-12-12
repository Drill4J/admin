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
package com.epam.drill.admin.metrics.config

import io.ktor.server.config.*

/**
 * Configuration for test recommendations.
 */
class TestRecommendationsConfig(private val config: ApplicationConfig) {

    /**
     * Period of days from now by default to get the coverage data.
     */
    val coveragePeriodDays: Int
        get() = config.propertyOrNull("coveragePeriodDays")?.getString()?.toIntOrNull() ?: 30
}