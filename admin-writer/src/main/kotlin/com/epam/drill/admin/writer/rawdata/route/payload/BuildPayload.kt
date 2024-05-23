package com.epam.drill.admin.writer.rawdata.route.payload

import kotlinx.serialization.Serializable

@Serializable
data class BuildPayload(
    val groupId: String,
    val appId: String,
    val commitSha: String = "",
    val buildVersion: String = "",
    val branch: String = "",
    val commitDate: String = "", // TODO use actual date/timestamp format
    val commitMessage: String = "",
    val commitAuthor: String = "",
    val commitTags: String = "",
)