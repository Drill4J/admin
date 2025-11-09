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
import io.ktor.client.request.prepareGet
import kotlinx.coroutines.runBlocking

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

    suspend infix fun MethodComparison.comparedTo(other: InstancePayload) {
        val otherMethods = builds.getOrDefault(other, ArrayList())
        val methods = builds.getOrDefault(this.build, ArrayList(otherMethods))
        builds.put(this.build, methods)
        when (this.changeType) {
            ChangeType.NEW -> {
                methods.add(this.method)
            }

            ChangeType.MODIFIED -> {
                methods.removeIf { isSignatureEqual(it, this.method) }
                methods.add(this.method.changed())
            }

            ChangeType.DELETED -> {
                methods.removeIf { isSignatureEqual(it, this.method) }
            }
        }
        client.deployInstance(this.build, methods.toTypedArray())
    }

    private fun isSignatureEqual(one: SingleMethodPayload, other: SingleMethodPayload) =
        one.classname == other.classname &&
                one.name == other.name &&
                one.params == other.params &&
                one.returnType == other.returnType


    suspend infix fun TestDetails.covers(method: SingleMethodPayload): TestCoverageMap {
        return TestCoverageMap(this, method, IntArray(method.probesCount) { 1 })
    }

    suspend infix fun TestCoverageMap.with(probes: IntArray): TestCoverageMap {
        return TestCoverageMap(this.test, this.method, probes)
    }

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

    inner class TestCoverageMap(
        val test: TestDetails,
        val method: SingleMethodPayload,
        val probes: IntArray
    )

    inner class MethodCoverage(
        val method: SingleMethodPayload,
        val probes: IntArray
    )

    inner class MethodComparison(
        val build: InstancePayload,
        val method: SingleMethodPayload,
        val changeType: ChangeType
    )
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

    data class ImpactedMethods(
        val methods: List<SingleMethodPayload>,
        val build: InstancePayload,
        val impactedStatus: TestImpactStatus,
    )
}
