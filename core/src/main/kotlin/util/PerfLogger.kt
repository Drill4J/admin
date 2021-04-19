/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.admin.util

import mu.*
import kotlin.time.*

val logger = KotlinLogging.logger {}

//TODO it duplicates in test2code
inline fun <T> trackTime(tag: String = "", debug: Boolean = true, block: () -> T) =
    measureTimedValue { block() }.apply {
        when {
            duration.inSeconds > 1 -> {
                logger.warn { "[$tag] took: $duration" }
            }
            duration.inSeconds > 30 -> {
                logger.error { "[$tag] took: $duration" }
            }
            else -> if (debug) logger.debug { "[$tag] took: $duration" } else logger.trace { "[$tag] took: $duration" }
        }
    }.value
