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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LambdaDiffTest {
    private val methodExample = Method(
        "foo/bar/Bar",
        "method",
        "()V",
        hash = "123"
    )

    @Test
    fun `diff - lambda has not changed, method has changed`() {
        val firstBuildMethods = listOf(methodExample)
        val secondBuildMethods = listOf(methodExample.copy(hash = "896"))
        val diff = firstBuildMethods.diff(secondBuildMethods)
        assertTrue { diff.modified.isNotEmpty() }
        assertEquals(methodExample, diff.modified.first())
    }

    @Test
    fun `diff - method without lambda has changed`() {
        val firstBuildMethods = listOf(methodExample)
        val secondBuildMethods = listOf(methodExample.copy(hash = "896"))
        val diff = firstBuildMethods.diff(secondBuildMethods)
        assertTrue { diff.modified.isNotEmpty() }
        assertEquals(methodExample, diff.modified.first())
    }

    @Test
    fun `diff - method without lambda doesn't changed`() {
        val firstBuildMethods = listOf(methodExample)
        val secondBuildMethods = listOf(methodExample)
        val diff = firstBuildMethods.diff(secondBuildMethods)
        assertTrue { diff.modified.isEmpty() }
        assertTrue { diff.unaffected.isNotEmpty() }
        assertEquals(methodExample, diff.unaffected.first())
    }

    @Test
    fun `diff - lambda name has changed`() {
        val firstBuildMethods = listOf(methodExample)
        val secondBuildMethods = listOf(
            methodExample
        )
        val diff = firstBuildMethods.diff(secondBuildMethods)
        assertTrue { diff.modified.isEmpty() }
        assertTrue { diff.unaffected.isNotEmpty() }
        assertEquals(methodExample, diff.unaffected.first())
    }

}