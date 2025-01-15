package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.entity.TestLaunch

interface TestLaunchRepository {
    fun createMany(testLaunchList: List<TestLaunch>)
}