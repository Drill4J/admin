package com.epam.drill.admin.writer.rawdata.route

import com.epam.drill.admin.writer.rawdata.repository.MetricsRepository
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

@Location("/metrics")
object Metrics {
    @Location("/risks")
    data class Risks(
        val groupId: String,
        val agentId: String,
        val currentBranch: String,
        val currentVcsRef: String,
        val baseBranch: String,
        val baseVcsRef: String,
    )

    @Location("/coverage")
    data class Coverage(
        val groupId: String,
        val agentId: String,
        val currentVcsRef: String,
    )

    @Location("/summary")
    data class Summary(
        val groupId: String,
        val agentId: String,
        val currentBranch: String,
        val currentVcsRef: String,
        val baseBranch: String,
        val baseVcsRef: String,
    )
}

fun Route.metricRoutes() {
    getRisks()
    getCoverage()
    getSummary()
}


fun Route.getRisks() {
    val metricsRepository by closestDI().instance<MetricsRepository>()

    get<Metrics.Risks> { params ->
        val risks = metricsRepository.getRisksByBranchDiff(
            params.groupId,
            params.agentId,
            params.currentBranch,
            params.currentVcsRef,
            params.baseBranch,
            params.baseVcsRef
        )
        this.call.respond(HttpStatusCode.OK, risks)
    }
}

fun Route.getCoverage() {
    val metricsRepository by closestDI().instance<MetricsRepository>()

    get<Metrics.Coverage> { params ->
        val coverage = metricsRepository.getTotalCoverage(
            params.groupId,
            params.agentId,
            params.currentVcsRef
        )
        this.call.respond(HttpStatusCode.OK, coverage)
    }
}

fun Route.getSummary() {
    val metricsRepository by closestDI().instance<MetricsRepository>()

    get<Metrics.Summary> { params ->
        val summary = metricsRepository.getSummaryByBranchDiff(
            params.groupId,
            params.agentId,
            params.currentBranch,
            params.currentVcsRef,
            params.baseBranch,
            params.baseVcsRef
        )
        this.call.respond(HttpStatusCode.OK, summary)
    }
}