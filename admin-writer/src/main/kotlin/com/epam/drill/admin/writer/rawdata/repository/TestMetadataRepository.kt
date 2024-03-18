package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.entity.TestMetadata

interface TestMetadataRepository {
    fun createMany(data: List<TestMetadata>)
}