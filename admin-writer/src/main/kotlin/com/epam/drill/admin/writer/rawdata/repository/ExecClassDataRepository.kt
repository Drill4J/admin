package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.entity.RawCoverageData
import com.epam.drill.admin.writer.rawdata.table.ExecClassDataTable
import org.jetbrains.exposed.sql.batchInsert

object ExecClassDataRepository {
    fun createMany(data: List<RawCoverageData>) {
        ExecClassDataTable.batchInsert(data, shouldReturnGeneratedValues = false) {
            this[ExecClassDataTable.instanceId] = it.instanceId
            this[ExecClassDataTable.className] = it.className
            this[ExecClassDataTable.testId] = it.testId
            this[ExecClassDataTable.probes] = it.probes
        }
    }
}