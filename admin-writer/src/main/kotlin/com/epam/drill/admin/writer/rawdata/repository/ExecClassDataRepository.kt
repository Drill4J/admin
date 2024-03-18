package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.entity.RawCoverageData

interface ExecClassDataRepository {
    fun createMany(data: List<RawCoverageData>)
    fun findAllByInstanceIds(instanceIds: List<String>): List<RawCoverageData>
}