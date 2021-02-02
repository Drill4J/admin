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
package com.epam.drill.admin.websockets

import com.epam.drill.admin.endpoints.*
import io.ktor.http.*
import kotlin.reflect.*
import kotlin.test.*

typealias Pair = kotlin.Pair<Map.Entry<URLTopic, kotlin.Pair<KClass<*>, CallbackWrapper<Any, Any>>>, Parameters>

val Pair.url
    get() = first.key

val Pair.parameters
    get() = second

internal class WsTopicKtTest {

    private val testMutableMap = mutableMapOf(
        URLTopic("/{any}/{anywhere}/{anyhow}") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper {}),
        URLTopic("/{any}/{anywhere}/xxx") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper {}),
        URLTopic("/{any}/xxx/{anywhere}") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper {}),
        URLTopic("/{any}/xxx/yyy") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper {}),
        URLTopic("/xxx/{any}/{anywhere}") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper {}),
        URLTopic("/xxx/{any}/yyy") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper {}),
        URLTopic("/xxx/yyy/{any}") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper<Any, Any> {})
    )

    private fun par(st: String) = testMutableMap.suitableRoutWithParameters(st)


    @Test
    fun `check method find Map And Parameters By Url`() {
        with(par("/xxx/random2/yyy")) {
            assertEquals(URLTopic("/xxx/{any}/yyy"), this.url)
            assertEquals(this.parameters["any"], "random2")
        }

        with(par("/random1/xxx/yyy")) {
            assertEquals(URLTopic("/{any}/xxx/yyy"), this.url)
            assertEquals("random1", this.parameters["any"])
        }

        with(par("/xxx/yyy/random3")) {
            assertEquals(URLTopic("/xxx/yyy/{any}"), this.url)
            assertEquals("random3", this.parameters["any"])
        }

        with(par("/xxx/random2/random3")) {
            assertEquals(URLTopic("/xxx/{any}/{anywhere}"), this.url)
            assertEquals("random2", this.parameters["any"])
            assertEquals("random3", this.parameters["anywhere"])
        }

        with(par("/random1/xxx/random3")) {
            assertEquals(URLTopic("/{any}/xxx/{anywhere}"), this.url)
            assertEquals("random1", this.parameters["any"])
            assertEquals("random3", this.parameters["anywhere"])
        }

        with(par("/random1/random2/xxx")) {
            assertEquals(URLTopic("/{any}/{anywhere}/xxx"), this.url)
            assertEquals("random2", this.parameters["anywhere"])
            assertEquals("random1", this.parameters["any"])
        }

        with(par("/random1/random2/random3")) {
            assertEquals(URLTopic("/{any}/{anywhere}/{anyhow}"), this.url)
            assertEquals("random1", this.parameters["any"])
            assertEquals("random2", this.parameters["anywhere"])
            assertEquals("random3", this.parameters["anyhow"])
        }

    }


}
