package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.entity.TestLaunch
import java.time.LocalDate

interface TestLaunchRepository {
    fun createMany(testLaunchList: List<TestLaunch>)
    fun deleteAllCreatedBefore(groupId: String, createdBefore: LocalDate)
}