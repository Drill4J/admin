package com.epam.drill.admin.metrics

import com.epam.drill.admin.writer.rawdata.route.payload.BuildPayload
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.test.assertEquals

suspend fun HttpClient.putBuild(payload: BuildPayload): HttpResponse {
    return put("/data-ingest/builds") {
        setBody(payload)
    }.apply {
        assertEquals(HttpStatusCode.OK, status)
    }
}