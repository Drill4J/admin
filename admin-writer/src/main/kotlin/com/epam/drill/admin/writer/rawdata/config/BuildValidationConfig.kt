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
package com.epam.drill.admin.writer.rawdata.config

import io.ktor.server.config.ApplicationConfig

/**
 * Configuration for build finalization/integrity validation.
 */
class BuildValidationConfig(private val config: ApplicationConfig) {
    /**
     * Maximum time window (in minutes), counted from the moment the build's finalization
     * (methods count/checksum) info arrived, during which the build's integrity
     * is retried by [retryJobCron]. Once this window elapses without a successful validation, the
     * build is marked INVALID.
     */
    val maxValidationWindowMinutes: Long
        get() = config.propertyOrNull("maxValidationWindowMinutes")?.getString()?.toLongOrNull() ?: 60

    /**
     * Cron expression defining how often the retry job scans for builds due for re-validation.
     */
    val retryJobCron: String
        get() = config.propertyOrNull("retryJobCron")?.getString() ?: "0 * * * * ?"
}
