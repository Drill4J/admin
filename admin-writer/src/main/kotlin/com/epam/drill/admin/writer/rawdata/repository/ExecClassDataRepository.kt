package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.entity.RawCoverageData
import com.epam.drill.admin.writer.rawdata.table.ExecClassDataTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.select

object ExecClassDataRepository {
    fun createMany(data: List<RawCoverageData>) {
        ExecClassDataTable.batchInsert(data, shouldReturnGeneratedValues = false) {
            this[ExecClassDataTable.instanceId] = it.instanceId
            this[ExecClassDataTable.className] = it.className
            this[ExecClassDataTable.testId] = it.testId
            this[ExecClassDataTable.probes] = it.probes
        }
    }

    fun findAllByInstanceIds(instanceIds: List<String>): List<RawCoverageData> {
        return ExecClassDataTable
            .select { ExecClassDataTable.instanceId inList instanceIds }
            .map { it.toRawCoverageData() }
    }

    // TODO classId and sessionId are omitted. Decide if they are required
    private fun ResultRow.toRawCoverageData() = RawCoverageData(
        instanceId = this[ExecClassDataTable.instanceId],
        className = this[ExecClassDataTable.className],
        testId = this[ExecClassDataTable.testId],
        probes = this[ExecClassDataTable.probes]
    )

}