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
package com.epam.drill.admin.writer.rawdata.util

import java.security.MessageDigest

/**
 * Calculates MD5 hash for the string and returns it as a lowercase hex string.
 */
fun String.md5(): String {
    val digest = MessageDigest.getInstance("MD5").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}

/**
 * Thrown when a method's body checksum cannot be parsed as a base-36 CRC64 value.
 */
class InvalidChecksumException(checksum: String) : Exception("Invalid checksum value: $checksum")

/**
 * Combines the CRC64 checksums (stringified as signed base-36, e.g. "-o40ap3ip2wwz") of all
 * methods of a build into a single build checksum by summing them modulo 2^64 (i.e. relying on
 * natural `Long` overflow), then re-encoding the result the same way.
 *
 * Being a commutative sum, the result does not depend on the order of the input checksums,
 * which makes it suitable for comparing a build's checksum regardless of the order in which its
 * methods were sent/stored.
 *
 * @throws InvalidChecksumException if any of the checksums is not a valid base-36 value.
 */
fun combineChecksumsCrc64(checksums: Iterable<String>): String {
    val sum = checksums.filter { it.isNotEmpty() }.fold(0L) { acc, checksum ->
        acc + (checksum.toLongOrNull(CHECKSUM_RADIX) ?: throw InvalidChecksumException(checksum))
    }
    return sum.toString(CHECKSUM_RADIX)
}

/**
 * Radix used to (de)serialize CRC64 checksum values as compact alphanumeric strings
 * (via `Long.toString(36)` / `String.toLong(36)`), e.g. "-o40ap3ip2wwz".
 */
private const val CHECKSUM_RADIX = 36
