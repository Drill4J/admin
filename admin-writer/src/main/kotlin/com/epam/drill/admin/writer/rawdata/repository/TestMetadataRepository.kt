package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.entity.TestMetadata
import com.epam.drill.admin.writer.rawdata.table.TestMetadataTable
import org.jetbrains.exposed.sql.batchInsert

object TestMetadataRepository {
    fun createMany(data: List<TestMetadata>) {
        TestMetadataTable.batchInsert(data, shouldReturnGeneratedValues = false) {
            this[TestMetadataTable.testId] = it.testId
            this[TestMetadataTable.name] = it.name
            this[TestMetadataTable.type] = it.type
        }
    }
}