package com.epam.drill.admin.metrics

import com.epam.drill.admin.writer.rawdata.route.payload.InstancePayload
import com.epam.drill.admin.writer.rawdata.route.payload.SingleMethodPayload
import com.epam.drill.admin.writer.rawdata.route.payload.TestDetails
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import kotlin.test.assertTrue

suspend fun HttpClient.getImpactedTests(
    build: InstancePayload,
    baselineBuild: InstancePayload,
    otherParameters: HttpRequestBuilder.() -> Unit = {}
): HttpResponse {
    return get("/metrics/impacted-tests") {
        parameter("groupId", build.groupId)
        parameter("appId", build.appId)
        parameter("buildVersion", build.buildVersion)
        parameter("baselineBuildVersion", baselineBuild.buildVersion)
        otherParameters()
    }.assertSuccessStatus()
}

suspend fun HttpClient.getImpactedMethods(
    build: InstancePayload,
    baselineBuild: InstancePayload,
    otherParameters: HttpRequestBuilder.() -> Unit = {}
): HttpResponse {
    return get("/metrics/impacted-methods") {
        parameter("groupId", build.groupId)
        parameter("appId", build.appId)
        parameter("buildVersion", build.buildVersion)
        parameter("baselineBuildVersion", baselineBuild.buildVersion)
        otherParameters()
    }.assertSuccessStatus()
}

fun TestDetails.assertTestIsImpacted(data: List<Map<String, Any?>>) {
    assertTrue(data.any {
        it["testName"] == this.testName && it["testPath"] == this.path
    }, "Expected test [${this.testName}] to be impacted, but it was not found in response of impacted tests.")
}

fun TestDetails.assertTestIsNotImpacted(data: List<Map<String, Any?>>) {
    assertTrue(data.none {
        it["testName"] == this.testName && it["testPath"] == this.path
    }, "Expected test [${this.testName}] to be not impacted, but it was found in response of impacted tests.")
}

fun TestDetails.assertTestHasUnknownImpact(data: List<Map<String, Any?>>) {
    assertTrue(data.none {
        it["testName"] == this.testName && it["testPath"] == this.path
    }, "Expected test [${this.testName}] to have unknown impact, but it was found in response of impacted tests.")
}

fun SingleMethodPayload.assertMethodIsImpacted(data: List<Map<String, Any?>>) {
    assertTrue(data.any {
        it["name"] == this.name && it["className"] == this.classname
    }, "Expected method [${this.classname}.${this.name}] to be impacted, but it was not found in response of impacted methods.")
}

fun SingleMethodPayload.assertMethodIsNotImpacted(data: List<Map<String, Any?>>) {
    assertTrue(data.none {
        it["name"] == this.name && it["className"] == this.classname
    }, "Expected method [${this.classname}.${this.name}] to be not impacted, but it was found in response of impacted methods.")
}

fun SingleMethodPayload.assertMethodHasUnknownImpact(data: List<Map<String, Any?>>) {
    assertTrue(data.none {
        it["name"] == this.name && it["className"] == this.classname
    }, "Expected method [${this.classname}.${this.name}] to be not impacted, but it was found in response of impacted methods.")
}