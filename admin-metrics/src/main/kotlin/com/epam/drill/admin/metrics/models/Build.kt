package com.epam.drill.admin.metrics.models

import com.epam.drill.admin.common.service.generateBuildId

open class Build(
    val groupId: String,
    val appId: String,
    val instanceId: String? = null,
    val commitSha: String? = null,
    val buildVersion: String? = null,
    val errorMessage: String = "Provide at least one of the following: instanceId, commitSha or buildVersion"
) {
    val id: String
        get() = generateBuildId(
            groupId = groupId,
            appId = appId,
            instanceId = instanceId,
            commitSha = commitSha,
            buildVersion = buildVersion,
            errorMsg = errorMessage
        )
}