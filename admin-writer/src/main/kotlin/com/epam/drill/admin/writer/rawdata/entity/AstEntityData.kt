package com.epam.drill.admin.writer.rawdata.entity

data class AstEntityData(
    val instanceId: String,
    val className: String,
    val name: String,
    val params: String,
    val returnType: String,
    val probesCount: Int,
    val probesStartPos: Int,
    val bodyChecksum: String,
)