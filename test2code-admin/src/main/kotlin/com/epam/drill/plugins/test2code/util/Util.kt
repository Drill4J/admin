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

import com.epam.dsm.util.*
import java.net.*
import java.util.*

fun currentTimeMillis() = System.currentTimeMillis()

fun genUuid() = "${UUID.randomUUID()}"

internal val availableProcessors = Runtime.getRuntime().availableProcessors()

tailrec fun Int.gcd(other: Int): Int = takeIf { other == 0 } ?: other.gcd(rem(other))

internal fun String.urlDecode(): String = takeIf { '%' in it }?.run {
    runCatching { URLDecoder.decode(this, "UTF-8") }.getOrNull()
} ?: this

internal fun String.urlEncode(): String = runCatching { URLEncoder.encode(this, "UTF-8") }.getOrNull() ?: this

val String.crc64: String get() = CRC64.calculateHash(toByteArray()).toString(Character.MAX_RADIX).weakIntern()

internal fun String.crc64(): Long = CRC64.calculateHash(toByteArray())

internal fun ByteArray.crc64(): Long = CRC64.calculateHash(this)

infix fun Number.percentOf(other: Number): Double = when (val dOther = other.toDouble()) {
    0.0 -> 0.0
    else -> toDouble() * 100.0 / dOther
}

fun String.isJson() = startsWith("{")
