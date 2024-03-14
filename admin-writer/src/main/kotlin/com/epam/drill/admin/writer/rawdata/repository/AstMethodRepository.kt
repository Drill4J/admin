package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.entity.AstEntityData
import com.epam.drill.admin.writer.rawdata.table.AstMethodTable
import org.jetbrains.exposed.sql.batchInsert

object AstMethodRepository {
    fun createMany(data: List<AstEntityData>) {
        AstMethodTable.batchInsert(data, shouldReturnGeneratedValues = false) {
            this[AstMethodTable.instanceId] = it.instanceId
            this[AstMethodTable.className] = it.className
            this[AstMethodTable.name] = it.name
            this[AstMethodTable.params] = it.params
            this[AstMethodTable.returnType] = it.returnType
            this[AstMethodTable.probesStartPos] = it.probesStartPos
            this[AstMethodTable.bodyChecksum] = it.bodyChecksum
            this[AstMethodTable.probesCount] = it.probesCount
        }
    }
}