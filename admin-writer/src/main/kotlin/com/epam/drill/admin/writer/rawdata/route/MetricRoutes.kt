package com.epam.drill.admin.writer.rawdata.route

import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig.transaction
import com.epam.drill.admin.writer.rawdata.config.executeQuery
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*

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
    get<Metrics.Risks> { params ->
        val risks = transaction {
            executeQuery("""
                SELECT * 
                  FROM raw_data.get_risks_by_branch_diff(?, ?, ?, ?, ?, ?)
            """.trimIndent(),
                params.groupId,
                params.agentId,
                params.currentVcsRef,
                params.currentBranch,
                params.baseBranch,
                params.baseVcsRef)
        }
        this.call.respond(HttpStatusCode.OK, risks)
    }
}

fun Route.getCoverage() {
    get<Metrics.Coverage> { params ->
        val risks = transaction {
            executeQuery("""
                SELECT raw_data.calculate_total_coverage_percent(?, ?, ?)
            """.trimIndent(),
                params.groupId,
                params.agentId,
                params.currentVcsRef)
        }
        this.call.respond(HttpStatusCode.OK, risks)
    }
}

fun Route.getSummary() {
    get<Metrics.Summary> { params ->
        val risks = transaction {
            executeQuery("""
                SELECT raw_data.calculate_total_coverage_percent(?, ?, ?) as coverage,
                       (SELECT count(*) 
                          FROM raw_data.get_risks_by_branch_diff(?, ?, ?, ?, ?, ?)) as risks
            """.trimIndent(),

                params.groupId,
                params.agentId,
                params.currentVcsRef,

                params.groupId,
                params.agentId,
                params.currentVcsRef,
                params.currentBranch,
                params.baseBranch,
                params.baseVcsRef)
        }
        this.call.respond(HttpStatusCode.OK, risks.first())
    }
}