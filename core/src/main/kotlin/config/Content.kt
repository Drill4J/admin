package com.epam.drill.admin.config

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.serialization.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*

fun ContentNegotiation.Configuration.converters() {
    serialization()
    register(ContentType.Any, EmptyContentConverter)
}

private object EmptyContentConverter : ContentConverter {
    override suspend fun convertForReceive(
        context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>
    ): Any? {
        val request = context.subject
        val readChannel = request.value as? ByteReadChannel
        val discarded = readChannel?.discard()
        return Unit.takeIf { discarded == 0L && request.type == it::class }
    }

    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any
    ): Any? = value
}
