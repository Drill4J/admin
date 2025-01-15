package com.epam.drill.admin.writer.rawdata.repository.impl

import com.epam.drill.admin.writer.rawdata.entity.TestLaunch
import com.epam.drill.admin.writer.rawdata.repository.TestLaunchRepository
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import java.time.LocalDate

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

    override fun deleteAllCreatedBefore(groupId: String, createdBefore: LocalDate) {
        TestLaunchTable.deleteWhere { (TestLaunchTable.groupId eq groupId) and (TestLaunchTable.createdAt less createdBefore.atStartOfDay()) }
    }
}