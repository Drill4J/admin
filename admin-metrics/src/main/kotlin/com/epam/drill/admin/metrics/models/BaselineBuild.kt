package com.epam.drill.admin.metrics.models

open class BaselineBuild(
    groupId: String,
    appId: String,
    baselineInstanceId: String? = null,
    baselineCommitSha: String? = null,
    baselineBuildVersion: String? = null,
): Build(
    groupId = groupId,
    appId = appId,
    instanceId = baselineInstanceId,
    commitSha = baselineCommitSha,
    buildVersion = baselineBuildVersion,
    errorMessage = "Provide at least one the following: baselineInstanceId, baselineCommitSha, baselineBuildVersion"
)