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

/**
 * CRC64 checksum calculator based on the polynom specified in ISO 3309. The
 * implementation is based on the following publications:
 *
 * <ul>
 * <li>http://en.wikipedia.org/wiki/Cyclic_redundancy_check</li>
 * <li>http://www.geocities.com/SiliconValley/Pines/8659/crc.htm</li>
 * </ul>
 */
object CRC64 {
    private const val POLY64REV = -0x2800000000000000L
    private val LOOKUPTABLE: LongArray

    init {
        LOOKUPTABLE = LongArray(0x100)
        for (i in 0..0xff) {
            var v = i.toLong()
            for (j in 0..7) {
                v = if (v and 1L == 1L) {
                    v ushr 1 xor POLY64REV
                } else {
                    v ushr 1
                }
            }
            LOOKUPTABLE[i] = v
        }
    }

    /**
     * Calculate the CRC64 checksum for the given data array
     *
     * @param data data to calculate checksum for
     * @return checksum value
     */
    fun calculateHash(data: ByteArray): Long {
        var sum: Long = 0
        for (b in data) {
            val lookupidx = sum.toInt() xor b.toInt() and 0xff
            sum = sum ushr 8 xor LOOKUPTABLE[lookupidx]
        }
        return sum
    }
}