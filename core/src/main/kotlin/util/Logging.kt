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
package com.epam.drill.admin.util

import ch.qos.logback.classic.pattern.*
import ch.qos.logback.classic.spi.*
import com.epam.drill.admin.config.*

fun String.limited() = takeIf {
    LOG_MESSAGE_MAX_LENGTH == 0 || it.length <= LOG_MESSAGE_MAX_LENGTH
} ?: "${substring(0, LOG_MESSAGE_MAX_LENGTH)}...message is too long, set a larger value for LOG_MESSAGE_MAX_LENGTH to see more."

class MsgConverter : ClassicConverter() {
    override fun convert(
        event: ILoggingEvent?
    ): String = event?.message?.limited() ?: ""
}
