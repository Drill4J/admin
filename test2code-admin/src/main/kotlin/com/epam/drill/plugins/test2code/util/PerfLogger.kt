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
package com.epam.drill.plugins.test2code.util

import com.epam.drill.plugins.test2code.*
import kotlin.time.*

val logger = logger {}

inline fun <T> trackTime(tag: String = "", debug: Boolean = true, block: () -> T) =
    measureTimedValue { block() }.apply {
        val message = "[$tag] took: $duration"
        when {
            duration.toDouble(DurationUnit.SECONDS) > 1 -> {
                logger.warn { message }
            }
            duration.toDouble(DurationUnit.SECONDS) > 30 -> {
                logger.error { message }
            }
            else -> if (debug) logger.debug { message } else logger.trace { message }
        }
    }.value
