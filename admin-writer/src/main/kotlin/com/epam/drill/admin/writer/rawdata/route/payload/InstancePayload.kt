package com.epam.drill.admin.writer.rawdata.route.payload

import kotlinx.serialization.Serializable

@Serializable
class InstancePayload(
    val groupId: String,
    val appId: String,
    val instanceId: String,
    val commitSha: String = "",
    val buildVersion: String = "",
)