package com.epam.drill.admin.metrics

import com.epam.drill.admin.metrics.config.metricsDIModule
import com.epam.drill.admin.metrics.route.metricsManagementRoutes
import com.epam.drill.admin.metrics.route.metricsRoutes
import com.epam.drill.admin.test.drillApplication
import com.epam.drill.admin.test.drillClient
import com.epam.drill.admin.writer.rawdata.config.rawDataServicesDIModule
import com.epam.drill.admin.writer.rawdata.route.dataIngestRoutes
import com.epam.drill.admin.writer.rawdata.route.payload.InstancePayload
import com.epam.drill.admin.writer.rawdata.route.payload.SingleMethodPayload
import com.epam.drill.admin.writer.rawdata.route.payload.TestDetails
import io.ktor.client.HttpClient

suspend fun drill(testsData: suspend TestDsl.() -> Unit): HttpClient {
    return drillApplication(rawDataServicesDIModule, metricsDIModule) {
        dataIngestRoutes()
        metricsRoutes()
        metricsManagementRoutes()
    }.drillClient().apply {
        testsData(TestDsl(this))
        refreshMaterializedViews()
    }
}


class TestDsl(val client: HttpClient) {

    suspend infix fun InstancePayload.has(methods: Set<SingleMethodPayload>) {
        client.deployInstance(this, methods.toTypedArray())
    }
    suspend infix fun InstancePayload.hasModified(method: SingleMethodPayload) = BuildMethodsMap(this, setOf(method.changed()))
    suspend infix fun InstancePayload.hasNew(method: SingleMethodPayload) = BuildMethodsMap(this, setOf(method.changed()))
    suspend infix fun InstancePayload.hasDeleted(method: SingleMethodPayload) = BuildMethodsMap(this, setOf(method.changed()))
    suspend infix fun BuildMethodsMap.comparedTo(other: InstancePayload) {

        client.deployInstance(this.build, this.methods.toTypedArray())
    }

    suspend infix fun TestDetails.covers(method: SingleMethodPayload): TestCoverageMap {
        return TestCoverageMap(this, method, IntArray(method.probesCount) { 1 })
    }
    suspend fun SingleMethodPayload.withProbesOf(vararg probes: Int): MethodCoverage {
        return MethodCoverage(this, probes)
    }
    suspend infix fun TestDetails.covers(coverage: MethodCoverage): TestCoverageMap {
        return TestCoverageMap(this, coverage.method, coverage.probes)
    }

    suspend infix fun TestCoverageMap.on(build: InstancePayload) {
        client.launchTest(
            session1, this.test, build, arrayOf(
                this.method to this.probes
            )
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

    inner class BuildMethodsMap(
        val build: InstancePayload,
        val methods: Set<SingleMethodPayload>,
    )
}
