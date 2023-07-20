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

import kotlin.test.*

class MethodsTest {
    @Test
    fun `simple case for all diff categories`() {
        val new = Method(
            ownerClass = "foo/bar/Baz",
            name = "new",
            desc = "(Z)Z",
            hash = "0"
        )
        val modified = Method(
            ownerClass = "foo/bar/Baz",
            name = "modified",
            desc = "(I)V",
            hash = "11"
        )
        val unaffected = Method(
            ownerClass = "foo/bar/Baz",
            name = "unaffected",
            desc = "(Z)Z",
            hash = "0"
        )
        val deleted = Method(
            ownerClass = "foo/bar/Baz",
            name = "deleted",
            desc = "(I)V",
            hash = "2"
        )
        val old = listOf(
            unaffected,
            Method(
                ownerClass = "foo/bar/Baz",
                name = "modified",
                desc = "(I)V",
                hash = "1"
            ),
            deleted
        ).sorted()
        val current = listOf(
            new,
            unaffected,
            modified
        ).sorted()
        val diff = current.diff(old)
        assertEquals(listOf(new), diff.new)
        assertEquals(listOf(modified), diff.modified)
        assertEquals(listOf(deleted), diff.deleted)
        assertEquals(listOf(unaffected), diff.unaffected)
    }
}
