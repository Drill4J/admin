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

import com.epam.drill.admin.common.scheduler.DrillScheduler
import com.epam.drill.admin.metrics.config.metricsDIModule
import com.epam.drill.admin.metrics.job.UpdateMetricsEtlJob
import com.epam.drill.admin.metrics.route.metricsManagementRoutes
import com.epam.drill.admin.metrics.route.metricsRoutes
import com.epam.drill.admin.metrics.views.ChangeType
import com.epam.drill.admin.metrics.views.TestImpactStatus
import com.epam.drill.admin.test.StubDrillScheduler
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
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val scheduler = DI.Module("testModule") {
        bind<DrillScheduler>() with singleton {
            StubDrillScheduler(UpdateMetricsEtlJob(
                instance(), instance()
            ))
        }
    }

fun havingData(testsData: suspend TestDataDsl.() -> Unit): HttpClient {
    return runBlocking {
        drillApplication(scheduler, rawDataServicesDIModule, metricsDIModule) {
            dataIngestRoutes()
            metricsRoutes()
            metricsManagementRoutes()
        }.drillClient().apply {
            val testDataDsl = TestDataDsl(this)
            testsData(testDataDsl)
            testDataDsl.build()
            refreshMetrics()
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
    private val builds = linkedMapOf<InstancePayload, MutableList<SingleMethodPayload>>()

    suspend fun build() {
        builds.forEach { (b, m) ->
            client.deployInstance(b, m.toTypedArray())
        }
    }

    suspend infix fun InstancePayload.has(methods: List<SingleMethodPayload>) {
        val newMethods = methods.recalcProbesStartPos()
        builds.put(this, ArrayList(newMethods))
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
        val targetMethods = builds.getOrDefault(this.build, ArrayList(baselineMethods)).toMutableList()
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
        val newTargetMethods = targetMethods.recalcProbesStartPos()
        builds.put(this.build, newTargetMethods)
    }

    private fun List<SingleMethodPayload>.recalcProbesStartPos(): MutableList<SingleMethodPayload> {
        val resultList = mutableListOf<SingleMethodPayload>()
        var nextMethodProbesStartPos = 0
        this.forEach {
            val newMethod = it.setProbesStartPos(nextMethodProbesStartPos)
            nextMethodProbesStartPos = newMethod.probesStartPos + newMethod.probesCount
            resultList += newMethod
        }
        resultList.sortBy { it.probesStartPos }
        return resultList
    }

    suspend infix fun InstancePayload.hasTheSameMethodsComparedTo(
        baseline: InstancePayload
    ) {
        val otherMethods = builds.getOrDefault(baseline, ArrayList())
        builds.put(this, ArrayList(otherMethods))
    }

    private fun isSignatureEqual(one: SingleMethodPayload, other: SingleMethodPayload) =
        one.classname == other.classname &&
                one.name == other.name &&
                one.params == other.params &&
                one.returnType == other.returnType

    suspend infix fun TestCoverageMap.on(build: InstancePayload) {
        val methods = builds[build]?.associate {
            if (isSignatureEqual(it, this.method))
                it to this.probes
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
        client.getChanges(
            build = this.build,
            baselineBuild = baseline,
            includeDeleted = (this.changeType == ChangeType.DELETED),
            includeEqual = (this.changeType == ChangeType.EQUAL),
            otherParameters = parameters
        ).returns { data ->
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