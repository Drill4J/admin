/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    buildVersion = baselineBuildVersion
) {
    constructor(build: Build) : this(
        groupId = build.groupId,
        appId = build.appId,
        baselineInstanceId = build.instanceId,
        baselineCommitSha = build.commitSha,
        baselineBuildVersion = build.buildVersion
    )

    override val errorMessage = "Provide at least one the following: baselineInstanceId, baselineCommitSha, baselineBuildVersion"
}