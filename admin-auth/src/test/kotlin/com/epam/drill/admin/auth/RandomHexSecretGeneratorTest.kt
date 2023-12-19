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
package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.service.impl.RandomHexSecretGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testing [RandomHexSecretGenerator]
 */
class RandomHexSecretGeneratorTest {
    @Test
    fun `generate must return random string in HEX format`() {
        val result = RandomHexSecretGenerator().generate()
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `generate must return random string of given length`() {
        val result = RandomHexSecretGenerator(8).generate()
        assertEquals(result.length, 8 * 2) // HEX format has 2 characters per byte
    }
}