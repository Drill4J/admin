package com.epam.drill.admin.writer.rawdata.entity

data class TestMetadata (
    val testId: String,
    val name: String,
    val type: String,
    // TODO add field to store arbitrary data (key value? array?)
)