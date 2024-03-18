package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.entity.AstEntityData

interface AstMethodRepository {
    fun createMany(data: List<AstEntityData>)
    fun findAllByInstanceIds(instanceIds: List<String>): List<AstEntityData>
}