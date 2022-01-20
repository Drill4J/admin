package com.epam.drill.admin.util

import ch.qos.logback.classic.pattern.*
import ch.qos.logback.classic.spi.*
import com.epam.drill.admin.config.*

fun String.limited() = takeIf {
    LOG_MESSAGE_MAX_LENGTH == 0 || it.length <= LOG_MESSAGE_MAX_LENGTH
} ?: "${substring(0, LOG_MESSAGE_MAX_LENGTH)}...message is too long, set a larger value for LOG_MESSAGE_MAX_LENGTH to see more."

class MsgConverter : ClassicConverter() {
    override fun convert(
        event: ILoggingEvent?
    ): String = event?.message?.limited() ?: ""
}
