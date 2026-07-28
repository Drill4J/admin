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
package com.epam.drill.admin.metrics

import com.epam.drill.admin.writer.rawdata.route.payload.InstancePayload
import com.epam.drill.admin.writer.rawdata.route.payload.SingleMethodPayload
import com.epam.drill.admin.writer.rawdata.route.payload.TestDetails
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertTrue

suspend fun HttpClient.postImpactedTests(
    build: InstancePayload,
    baselineBuild: InstancePayload,
    otherParameters: MutableMap<String, Any?>.() -> Unit = {}
): HttpResponse {
    val extras = mutableMapOf<String, Any?>()
    extras.otherParameters()
    val body = buildJsonObject {
        put("groupId", build.groupId)
        put("appId", build.appId)
        put("buildVersion", build.buildVersion)
        put("baselineBuildVersion", baselineBuild.buildVersion)
        extras.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is String -> put(key, value)
                is Number -> put(key, value)
                is Boolean -> put(key, value)
                is List<*> -> put(
                    key,
                    JsonArray(value.map { item -> JsonPrimitive(item.toString()) })
                )
                else -> put(key, value.toString())
            }
        }
    }
    return post("/metrics/impacted-tests") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }.assertSuccessStatus()
}

suspend fun HttpClient.getBuildChanges(
    build: InstancePayload,
    baselineBuild: InstancePayload,
    hasImpactedTests: Boolean? = null,
    otherParameters: HttpRequestBuilder.() -> Unit = {}
): HttpResponse {
    return get("/metrics/build-changes") {
        parameter("groupId", build.groupId)
        parameter("appId", build.appId)
        parameter("buildVersion", build.buildVersion)
        parameter("baselineBuildVersion", baselineBuild.buildVersion)
        if (hasImpactedTests == true) {
            parameter("hasImpactedTests", true)
        }
        otherParameters()
    }.assertSuccessStatus()
}

suspend fun HttpClient.getCoverage(
    build: InstancePayload,
    otherParameters: HttpRequestBuilder.() -> Unit
): HttpResponse {
    return get("/metrics/coverage") {
        parameter("groupId", build.groupId)
        parameter("appId", build.appId)
        parameter("buildVersion", build.buildVersion)
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
    assertTrue(
        data.any {
            it["name"] == this.name && it["className"] == this.classname
        },
        "Expected method [${this.classname}.${this.name}] to be impacted, but it was not found in response of build changes."
    )
}

fun SingleMethodPayload.assertMethodIsNotImpacted(data: List<Map<String, Any?>>) {
    assertTrue(
        data.none {
            it["name"] == this.name && it["className"] == this.classname
        },
        "Expected method [${this.classname}.${this.name}] to be not impacted, but it was found in response of build changes."
    )
}

fun SingleMethodPayload.assertMethodHasUnknownImpact(data: List<Map<String, Any?>>) {
    assertTrue(
        data.none {
            it["name"] == this.name && it["className"] == this.classname
        },
        "Expected method [${this.classname}.${this.name}] to be not impacted, but it was found in response of build changes."
    )
}

fun SingleMethodPayload.assertMethodIsModified(data: List<Map<String, Any?>>) {
    val actual = data.find {
        it["name"] == this.name
    }
    assertTrue(
        actual != null,
        "Expected method [${this.classname}.${this.name}] to be modified, but it was not found in response of build changes."
    )
    assertTrue(
        actual["changeType"] == com.epam.drill.admin.metrics.views.ChangeType.MODIFIED.name,
        "Expected method [${this.classname}.${this.name}] to be modified, but it was marked as ${actual["changeType"]} in response of build changes."
    )
}

fun SingleMethodPayload.assertMethodIsNew(data: List<Map<String, Any?>>) {
    val actual = data.find {
        it["name"] == this.name
    }
    assertTrue(
        actual != null,
        "Expected method [${this.classname}.${this.name}] to be new, but it was not found in response of build changes."
    )
    assertTrue(
        actual["changeType"] == com.epam.drill.admin.metrics.views.ChangeType.NEW.name,
        "Expected method [${this.classname}.${this.name}] to be new, but it was marked as ${actual["changeType"]} in response of build changes."
    )
}

fun SingleMethodPayload.assertMethodIsEqual(data: List<Map<String, Any?>>) {
    val actual = data.find {
        it["name"] == this.name
    }
    assertTrue(
        actual != null,
        "Expected method [${this.classname}.${this.name}] to be equal, but it was not found in response of build changes."
    )
    assertTrue(
        actual["changeType"] == com.epam.drill.admin.metrics.views.ChangeType.EQUAL.name,
        "Expected method [${this.classname}.${this.name}] to be equal, but it was marked as ${actual["changeType"]} in response of build changes."
    )
}

fun SingleMethodPayload.assertMethodIsDeleted(data: List<Map<String, Any?>>) {
    val actual = data.find {
        it["name"] == this.name
    }
    assertTrue(
        actual != null,
        "Expected method [${this.classname}.${this.name}] to be deleted, but it was not found in response of build changes."
    )
    assertTrue(
        actual["changeType"] == com.epam.drill.admin.metrics.views.ChangeType.DELETED.name,
        "Expected method [${this.classname}.${this.name}] to be deleted, but it was marked as ${actual["changeType"]} in response of build changes."
    )
}

fun SingleMethodPayload.assertThatMethodIsCoveredBy(
    data: List<Map<String, Any?>>,
    expectedRate: Double,
    tolerance: Double = 0.01
) {
    val actual = data.find {
        it["name"] == this.name && it["className"] == this.classname
    }
    assertTrue(
        actual != null,
        "Expected method [${this.classname}.${this.name}] to be present in coverage data, but it was not found."
    )
    val actualCoveredProbes = actual["coveredProbes"] ?: 0
    val actualRate = (actualCoveredProbes as Int).toDouble() / this.probesCount

    assertTrue(
        kotlin.math.abs(actualRate - expectedRate) <= tolerance,
        "Expected method [${this.classname}.${this.name}] to have coverage rate of at least $expectedRate ± $tolerance, " +
                "but found $actualRate."
    )
}

fun SingleMethodPayload.assertThatMethodIsCovered(
    data: List<Map<String, Any?>>
) {
    val actual = data.find {
        it["name"] == this.name && it["className"] == this.classname
    }
    assertTrue(
        actual != null,
        "Expected method [${this.classname}.${this.name}] to be present in coverage data, but it was not found."
    )
    val actualCoveredProbes = (actual["coveredProbes"] ?: 0) as Int
    assertTrue(
        actualCoveredProbes > 0,
        "Expected method [${this.classname}.${this.name}] to be covered, but it has 0 covered probes."
    )
}

fun SingleMethodPayload.assertThatMethodIsNotCovered(
    data: List<Map<String, Any?>>
) {
    val actual = data.find {
        it["name"] == this.name && it["className"] == this.classname
    }
    assertTrue(
        actual != null,
        "Expected method [${this.classname}.${this.name}] to be present in coverage data, but it was not found."
    )
    val actualCoveredProbes = (actual["coveredProbes"] ?: 0) as Int
    assertTrue(
        actualCoveredProbes == 0,
        "Expected method [${this.classname}.${this.name}] to be not covered, but it has $actualCoveredProbes covered probes."
    )
}
