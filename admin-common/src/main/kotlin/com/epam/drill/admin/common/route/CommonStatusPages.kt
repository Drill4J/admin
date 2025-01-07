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
package com.epam.drill.admin.common.route

import com.epam.drill.admin.common.exception.BuildNotFound
import com.epam.drill.admin.common.exception.InvalidParameters
import io.ktor.server.plugins.statuspages.*
import kotlinx.serialization.MissingFieldException
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun StatusPagesConfig.commonStatusPages() {
    exception<IllegalArgumentException> { call, exception ->
        logger.trace(exception) { "400 Invalid or missing parameters" }
        call.validationError(exception, "Invalid or missing parameters")
    }
    exception<InvalidParameters> { call, exception ->
        logger.trace(exception) { "400 Invalid or missing query parameters" }
        call.validationError(exception, "Invalid or missing query parameters")
    }
    exception<BuildNotFound> { call, exception ->
        logger.trace(exception) { "422 Build not found" }
        call.unprocessableEntity(exception, "Build not found")
    }
    exception<MissingFieldException> { call, exception ->
        logger.trace(exception) { "400 MissingFieldException ${exception.message}" }
        call.validationError(exception)
    }
}