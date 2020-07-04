package com.epam.drill.e2e.plugin

import com.epam.drill.logger.api.*

object SimpleLogging : LoggerFactory {
    override fun logger(name: String): Logger = name.namedLogger(
        logLevel = { LogLevel.TRACE },
        appender = ::appendLog
    )
}

@Suppress("UNUSED_PARAMETER")
internal fun appendLog(name: String, level: LogLevel, t: Throwable?, marker: Marker?, msg: () -> Any?) {
    println("[$name][${level.name}] ${msg()}")
    t?.printStackTrace()
}
