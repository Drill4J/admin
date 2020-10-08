package com.epam.drill.admin.kodein

import io.ktor.application.*
import java.io.*

fun Application.onStop(block: () -> Unit) {
    environment.monitor.subscribe(ApplicationStopped) {
        runCatching(block)
    }
}

fun Application.closeOnStop(resource: Closeable) = onStop(resource::close)
