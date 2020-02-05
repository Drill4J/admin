package com.epam.drill.admin.endpoints

import io.ktor.client.utils.*
import io.ktor.http.*

fun Any?.toStatusResponsePair(): Pair<HttpStatusCode, Any> = this?.let {
    HttpStatusCode.OK to it
} ?: HttpStatusCode.NotFound to EmptyContent
