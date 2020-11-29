package com.epam.drill.admin.store

import com.epam.kodux.*
import kotlinx.coroutines.*

fun StoreClient.closeStore() {
    runBlocking {
        for (attempt in 1..3) {
            if (kotlin.runCatching { close() }.isSuccess) {
                break
            }
            delay(500L)
        }
    }
}
