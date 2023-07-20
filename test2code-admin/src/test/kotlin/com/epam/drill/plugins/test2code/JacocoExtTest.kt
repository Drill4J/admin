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

import com.epam.drill.plugins.test2code.util.crc64
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JacocoExtTest {

    @Test
    fun `crcr64 should return same values for equal strings`() {
        val str1 = javaClass.name.substringAfterLast(".")
        val str2 = javaClass.simpleName
        assertNotSame(str1, str2) //the strings should not be identical
        assertEquals(str1, str2)
        assertEquals(str1.crc64, str2.crc64)
    }

}