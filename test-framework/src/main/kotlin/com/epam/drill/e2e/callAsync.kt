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
package com.epam.drill.e2e

import kotlinx.coroutines.*
import kotlin.coroutines.*

private val asyncCallLoop = newFixedThreadPoolContext(10, "test-framework async calls")

object callAsync : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = asyncCallLoop

    operator fun invoke(context: CoroutineContext = EmptyCoroutineContext, vlock: suspend () -> Unit) = launch(context) {
        vlock()
    }
}
