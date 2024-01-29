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
package com.epam.drill.admin.config

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.serialization.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.streams.*
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.reflect.jvm.*
import kotlin.text.Charsets

fun ContentNegotiation.Configuration.converters() {
    json()
    register(ContentType.Application.ProtoBuf, SerializationConverter(ProtoBuf))
    register(ContentType.Any, EmptyContentConverter)
}

/**
 * Set UTF_8 as default charset for Content-Type application/json
 * Use see[io.ktor.request.ApplicationReceivePipeline.Before] because can't override the default transformation see[io.ktor.server.engine.installDefaultTransformations]
 */
fun Application.interceptorForApplicationJson() {
    receivePipeline.intercept(ApplicationReceivePipeline.Before) { query ->
        if (call.request.contentType() != ContentType.Application.Json) return@intercept
        val channel = query.value as? ByteReadChannel ?: return@intercept
        val result = when (query.typeInfo.jvmErasure) {
            String::class -> channel.readText(
                charset = call.request.contentCharset()
                    ?: Charsets.UTF_8
            )
            else -> null
        }
        if (result != null)
            proceedWith(ApplicationReceiveRequest(query.typeInfo, result, true))
    }
}

private object EmptyContentConverter : ContentConverter {
    override suspend fun convertForReceive(
        context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>,
    ): Any? {
        val request = context.subject
        val readChannel = request.value as? ByteReadChannel
        val discarded = readChannel?.discard()
        return Unit.takeIf { discarded == 0L && request.typeInfo.jvmErasure == it::class }
    }

    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any,
    ): Any = value
}

/**
 * Copied from here see[io.ktor.server.engine.readText]
 */
private suspend fun ByteReadChannel.readText(
    charset: Charset,
): String {
    if (isClosedForRead) return ""

    val content = readRemaining(Long.MAX_VALUE)

    return try {
        if (charset == Charsets.UTF_8) content.readText()
        else content.inputStream().reader(charset).readText()
    } finally {
        content.release()
    }
}
