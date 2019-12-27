package com.epam.drill.e2e

import kotlinx.coroutines.*
import kotlin.coroutines.*

private val asyncCallLoop = newFixedThreadPoolContext(10, "test-framework async calls")

object callAsync : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = asyncCallLoop

    operator fun invoke(vlock: suspend () -> Unit) = launch {
        vlock()
    }


}