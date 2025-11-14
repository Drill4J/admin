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

import com.epam.drill.admin.metrics.config.metricsDIModule
import com.epam.drill.admin.metrics.route.metricsManagementRoutes
import com.epam.drill.admin.metrics.route.metricsRoutes
import com.epam.drill.admin.metrics.views.ChangeType
import com.epam.drill.admin.metrics.views.TestImpactStatus
import com.epam.drill.admin.test.drillApplication
import com.epam.drill.admin.test.drillClient
import com.epam.drill.admin.writer.rawdata.config.rawDataServicesDIModule
import com.epam.drill.admin.writer.rawdata.route.dataIngestRoutes
import com.epam.drill.admin.writer.rawdata.route.payload.InstancePayload
import com.epam.drill.admin.writer.rawdata.route.payload.SingleMethodPayload
import com.epam.drill.admin.writer.rawdata.route.payload.TestDetails
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.coroutines.runBlocking
import kotlin.test.assertTrue

fun havingData(testsData: suspend TestDataDsl.() -> Unit): HttpClient {
    return runBlocking {
        drillApplication(rawDataServicesDIModule, metricsDIModule) {
            dataIngestRoutes()
            metricsRoutes()
            metricsManagementRoutes()
        }.drillClient().apply {
            testsData(TestDataDsl(this))
            refreshMaterializedViews()
        }
    }
}

class TestCoverageMap(
    val test: TestDetails,
    val method: SingleMethodPayload,
    val probes: IntArray
)

class MethodComparison(
    val build: InstancePayload,
    val method: SingleMethodPayload,
    val changeType: ChangeType
)

data class MethodCoverage(
    val method: SingleMethodPayload,
    val coverageRate: Double
)

val Int.percent: Double
    get() = this / 100.0

data class ImpactedMethods(
    val methods: List<SingleMethodPayload>,
    val build: InstancePayload,
    val impactedStatus: TestImpactStatus,
)

class TestDataDsl(val client: HttpClient) {
    private val builds = mutableMapOf<InstancePayload, MutableList<SingleMethodPayload>>()

    suspend infix fun InstancePayload.has(methods: List<SingleMethodPayload>) {
        builds.put(this, ArrayList(methods))
        client.deployInstance(this, methods.toTypedArray())
    }

    suspend infix fun InstancePayload.hasModified(method: SingleMethodPayload) =
        MethodComparison(this, method, ChangeType.MODIFIED)

    suspend infix fun InstancePayload.hasNew(method: SingleMethodPayload) =
        MethodComparison(this, method, ChangeType.NEW)

    suspend infix fun InstancePayload.hasDeleted(method: SingleMethodPayload) =
        MethodComparison(this, method, ChangeType.DELETED)

    suspend infix fun TestDetails.covers(method: SingleMethodPayload): TestCoverageMap {
        return TestCoverageMap(this, method, IntArray(method.probesCount) { 1 })
    }

    suspend infix fun TestCoverageMap.with(probes: IntArray): TestCoverageMap {
        return TestCoverageMap(this.test, this.method, probes)
    }

    suspend infix fun MethodComparison.comparedTo(baseline: InstancePayload) {
        val baselineMethods = builds.getOrDefault(baseline, ArrayList())
        val targetMethods = builds.getOrDefault(this.build, ArrayList(baselineMethods))
        builds.put(this.build, targetMethods)
        when (this.changeType) {
            ChangeType.NEW -> {
                targetMethods.add(this.method)
            }

            ChangeType.MODIFIED -> {
                targetMethods.removeIf { isSignatureEqual(it, this.method) }
                targetMethods.add(this.method.changed())
            }

            ChangeType.DELETED -> {
                targetMethods.removeIf { isSignatureEqual(it, this.method) }
            }

            ChangeType.EQUAL -> {
                // no changes
            }
        }
        client.deployInstance(this.build, targetMethods.toTypedArray())
    }

    suspend infix fun InstancePayload.hasTheSameMethodsComparedTo(
        baseline: InstancePayload
    ) {
        val otherMethods = builds.getOrDefault(baseline, ArrayList())
        builds.put(this, ArrayList(otherMethods))
        client.deployInstance(this, otherMethods.toTypedArray())
    }

    private fun isSignatureEqual(one: SingleMethodPayload, other: SingleMethodPayload) =
        one.classname == other.classname &&
                one.name == other.name &&
                one.params == other.params &&
                one.returnType == other.returnType

    suspend infix fun TestCoverageMap.on(build: InstancePayload) {
        val methods = builds[build]?.associate {
            if (isSignatureEqual(it, this.method))
                this.method to this.probes
            else
                it to IntArray(it.probesCount) { 0 }
        }?.map { it.key to it.value }?.toTypedArray() ?: arrayOf(
            this.method to this.probes
        )
        client.launchTest(
            session1, this.test, build, methods
        )
    }
}

fun HttpClient.expectThat(checks: suspend ExpectationDsl.(HttpClient) -> Unit) {
    val client = this
    return runBlocking {
        checks(ExpectationDsl(client), client)
    }
}

class ExpectationDsl(
    val client: HttpClient,
    var parameters: HttpRequestBuilder.() -> Unit = {}
) {

    suspend infix fun SingleMethodPayload.isEqualOn(build: InstancePayload) =
        MethodComparison(build, this, ChangeType.EQUAL)

    suspend infix fun SingleMethodPayload.isNewOn(build: InstancePayload) =
        MethodComparison(build, this, ChangeType.NEW)

    suspend infix fun SingleMethodPayload.isModifiedOn(build: InstancePayload) =
        MethodComparison(build, this, ChangeType.MODIFIED)

    suspend infix fun SingleMethodPayload.isDeletedOn(build: InstancePayload) =
        MethodComparison(build, this, ChangeType.DELETED)

    suspend infix fun MethodComparison.comparedTo(baseline: InstancePayload) {
        client.getChanges(this.build, baseline, parameters).returns { data ->
            when (this.changeType) {
                ChangeType.MODIFIED -> this.method.assertMethodIsModified(data)
                ChangeType.NEW -> this.method.assertMethodIsNew(data)
                ChangeType.DELETED -> this.method.assertMethodIsDeleted(data)
                ChangeType.EQUAL -> this.method.assertMethodIsEqual(data)
            }
        }
    }

    suspend infix fun SingleMethodPayload.isCoveredOn(build: InstancePayload) {
        client.getCoverage(build, parameters).returns { data ->
            this.assertThatMethodIsCovered(data)
        }
    }

    suspend infix fun SingleMethodPayload.isNotCoveredOn(build: InstancePayload) {
        client.getCoverage(build, parameters).returns { data ->
            this.assertThatMethodIsNotCovered(data)
        }
    }

    suspend infix fun SingleMethodPayload.isCoveredBy(percent: Double) = MethodCoverage(this, percent)

    suspend infix fun MethodCoverage.on(build: InstancePayload) {
        client.getCoverage(build, parameters).returns { data ->
            this.method.assertThatMethodIsCoveredBy(data, this.coverageRate)
        }
    }


    suspend fun with(parameters: HttpRequestBuilder.() -> Unit) {
        this.parameters = parameters
    }

    suspend infix fun TestDetails.isImpactedOn(build: InstancePayload) =
        ImpactedTests(listOf(this), build, TestImpactStatus.IMPACTED)

    suspend infix fun List<TestDetails>.areImpactedOn(build: InstancePayload) =
        ImpactedTests(this, build, TestImpactStatus.IMPACTED)

    suspend infix fun TestDetails.isNotImpactedOn(build: InstancePayload) =
        ImpactedTests(listOf(this), build, TestImpactStatus.NOT_IMPACTED)

    suspend infix fun TestDetails.hasUnknownImpactOn(build: InstancePayload) =
        ImpactedTests(listOf(this), build, TestImpactStatus.UNKNOWN_IMPACT)

    suspend infix fun ImpactedTests.comparedTo(baseline: InstancePayload) {
        client.getImpactedTests(this.build, baseline, parameters).returns { data ->
            when (this.impactStatus) {
                TestImpactStatus.IMPACTED -> this.tests.forEach { it.assertTestIsImpacted(data) }
                TestImpactStatus.NOT_IMPACTED -> this.tests.forEach { it.assertTestIsNotImpacted(data) }
                TestImpactStatus.UNKNOWN_IMPACT -> this.tests.forEach { it.assertTestHasUnknownImpact(data) }
            }
        }
    }

    data class ImpactedTests(
        val tests: List<TestDetails>,
        val build: InstancePayload,
        val impactStatus: TestImpactStatus,
    )

    suspend infix fun SingleMethodPayload.isImpactedOn(build: InstancePayload) =
        ImpactedMethods(listOf(this), build, TestImpactStatus.IMPACTED)

    suspend infix fun SingleMethodPayload.isNotImpactedOn(build: InstancePayload) =
        ImpactedMethods(listOf(this), build, TestImpactStatus.NOT_IMPACTED)

    suspend infix fun SingleMethodPayload.hasUnknownImpactOn(build: InstancePayload) =
        ImpactedMethods(listOf(this), build, TestImpactStatus.UNKNOWN_IMPACT)

    suspend infix fun ImpactedMethods.comparedTo(baseline: InstancePayload) {
        client.getImpactedMethods(this.build, baseline, parameters).returns { data ->
            when (this.impactedStatus) {
                TestImpactStatus.IMPACTED -> this.methods.forEach { it.assertMethodIsImpacted(data) }
                TestImpactStatus.NOT_IMPACTED -> this.methods.forEach { it.assertMethodIsNotImpacted(data) }
                TestImpactStatus.UNKNOWN_IMPACT -> this.methods.forEach { it.assertMethodHasUnknownImpact(data) }
            }
        }
    }
}