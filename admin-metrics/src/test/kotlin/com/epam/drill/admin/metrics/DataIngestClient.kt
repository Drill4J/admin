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

import com.epam.drill.admin.writer.rawdata.route.payload.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

private val counter = AtomicInteger(0)

suspend fun HttpClient.deployInstance(
    instance: InstancePayload,
    methods: Array<SingleMethodPayload>
) {
    putInstance(instance)
    putMethods(
        MethodsPayload(
            groupId = instance.groupId,
            appId = instance.appId,
            buildVersion = instance.buildVersion,
            methods = methods
        )
    )
}

suspend fun HttpClient.launchTest(
    test: TestDetails,
    instance: InstancePayload,
    coverage: Array<Pair<SingleMethodPayload, IntArray>>
) {
    val sessionId = "session-${counter.incrementAndGet()}"
    val testLaunchId = "test-launch-${counter.incrementAndGet()}"
    putTestSession(
        SessionPayload(
            groupId = instance.groupId,
            id = sessionId,
            testTaskId = testTask,
            startedAt = Clock.System.now()
        )
    )
    postTestMetadata(
        AddTestsPayload(
            groupId = instance.groupId,
            sessionId = sessionId,
            tests = listOf(
                TestLaunchInfo(
                    testLaunchId = testLaunchId,
                    testDefinitionId = test.definitionId,
                    result = TestResult.PASSED,
                    duration = 10,
                    details = test
                )
            )
        )
    )

    postCoverage(
        CoveragePayload(
            groupId = instance.groupId,
            appId = instance.appId,
            instanceId = instance.instanceId,
            coverage = arrayOf(
                SingleClassCoveragePayload(
                    classname = testClass,
                    testId = testLaunchId,
                    probes = coverage.toClassProbes()
                )
            )
        )
    )
}

val TestDetails.definitionId: String
    get() {
        return "$runner:$path:$testName"
    }

suspend fun HttpClient.putBuild(payload: BuildPayload): HttpResponse {
    return put("/data-ingest/builds") {
        setBody(payload)
    }.assertSuccessStatus()
}

suspend fun HttpClient.putInstance(payload: InstancePayload): HttpResponse {
    return put("/data-ingest/instances") {
        setBody(payload)
    }.assertSuccessStatus()
}

suspend fun HttpClient.putMethods(payload: MethodsPayload): HttpResponse {
    return put("/data-ingest/methods") {
        setBody(payload)
    }.assertSuccessStatus()
}

suspend fun HttpClient.postCoverage(payload: CoveragePayload): HttpResponse {
    return post("/data-ingest/coverage") {
        setBody(payload)
    }.assertSuccessStatus()
}

suspend fun HttpClient.postTestMetadata(payload: AddTestsPayload): HttpResponse {
    return post("/data-ingest/tests-metadata") {
        setBody(payload)
    }.assertSuccessStatus()
}

suspend fun HttpClient.putTestSession(payload: SessionPayload): HttpResponse {
    return put("/data-ingest/sessions") {
        setBody(payload)
    }.assertSuccessStatus()
}

fun HttpResponse.assertSuccessStatus() = also {
    assertEquals(HttpStatusCode.OK, status)
}

private fun Array<Pair<SingleMethodPayload, IntArray>>.toClassProbes(): BooleanArray {
    val classProbesCount = this.sumOf { it.first.probesCount }
    val classProbes = BooleanArray(classProbesCount)
    this.forEach { (method, probes) ->
        probes.forEachIndexed { index, probe -> classProbes[method.probesStartPos + index] = probe != 0 }
    }
    return classProbes
}