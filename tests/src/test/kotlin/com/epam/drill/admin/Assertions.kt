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
package com.epam.drill.admin

import kotlinx.coroutines.delay

/**
 * Calls the function block in a loop until it executes without an assertion check error
 * Inside the block, you should use functions such as `shouldBe` from the io.kotlintest package.
 * @see io.kotlintest.shouldBe
 */
suspend fun waitUntil(delayMs: Long = 50, block: suspend () -> Unit) {
    while (isFailed(block)) {
        delay(delayMs)
    }
}

private suspend fun isFailed(block: suspend () -> Unit) = try {
    block()
    false
} catch (e: AssertionError) {
    true
}
