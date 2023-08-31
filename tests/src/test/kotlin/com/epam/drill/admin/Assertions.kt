package com.epam.drill.admin

import kotlinx.coroutines.delay

/**
 * Calls the function block in a loop until it executes without an assertion check error
 * Inside the block, you should use functions such as `shouldBe` from the io.kotlintest package.
 * @see io.kotlintest.shouldBe
 */
suspend fun waitUntil(block: suspend () -> Unit) {
    while (isFailed(block)) {
        delay(10)
    }
}

private suspend fun isFailed(block: suspend () -> Unit) = try {
    block()
    false
} catch (e: AssertionError) {
    true
}
