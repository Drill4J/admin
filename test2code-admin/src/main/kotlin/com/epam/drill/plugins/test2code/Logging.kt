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
package com.epam.drill.plugins.test2code

import com.epam.dsm.util.*
import mu.*

private val packagePrefix: String = ::logger::class.qualifiedName?.run {
    substringBeforeLast('.').substringBeforeLast('.').weakIntern()
}.orEmpty()

internal fun logger(block: () -> Unit): KLogger = run {
    val name = block::class.qualifiedName?.run {
        if ('$' in this) substringBefore('$') else this
    }?.toLoggerName().orEmpty().weakIntern()
    KotlinLogging.logger(name)
}

internal fun Any.logger(vararg fields: String): KLogger = run {
    val name = this::class.qualifiedName?.toLoggerName().orEmpty()
    val suffix = fields.takeIf { it.any() }?.joinToString(prefix = "(", postfix = ")").orEmpty()
    KotlinLogging.logger("$name$suffix")
}

private fun String.toLoggerName(): String = removePrefix(packagePrefix).run {
    "plugins/${replace('.', '/')}"
}
