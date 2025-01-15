package com.epam.drill.admin.writer.rawdata.repository.impl

import com.epam.drill.admin.writer.rawdata.entity.TestLaunch
import com.epam.drill.admin.writer.rawdata.repository.TestLaunchRepository
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import org.jetbrains.exposed.sql.batchUpsert

class TestLaunchRepositoryImpl: TestLaunchRepository {
    override fun createMany(testLaunchList: List<TestLaunch>) {
        TestLaunchTable.batchUpsert(testLaunchList, shouldReturnGeneratedValues = false) {
            this[TestLaunchTable.id] = it.id
            this[TestLaunchTable.groupId] = it.groupId
            this[TestLaunchTable.testDefinitionId] = it.testDefinitionId
            this[TestLaunchTable.testSessionId] = it.testSessionId
            this[TestLaunchTable.result] = it.result
        }
    }
}