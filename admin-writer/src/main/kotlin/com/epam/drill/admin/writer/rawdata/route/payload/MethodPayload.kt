package com.epam.drill.admin.writer.rawdata.route.payload

import kotlinx.serialization.Serializable

@Serializable
class MethodsPayload(
    val groupId: String,
    val appId: String,
    val commitSha: String = "",
    val buildVersion: String = "",
    val instanceId: String = "",
    val methods: Array<SingleMethodPayload>
)

@Serializable
class SingleMethodPayload(
    val classname: String,
    val name: String,
    val params: String,
    val returnType: String,
    val probesCount: Int,
    val probesStartPos: Int,
    val bodyChecksum: String,
)