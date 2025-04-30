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
package com.epam.drill.admin.common.service

import com.epam.drill.admin.common.exception.InvalidParameters

const val BUILD_ID_SEPARATOR = ":"

fun generateBuildId(
    groupId: String,
    appId: String,
    instanceId: String?,
    commitSha: String?,
    buildVersion: String?,
    errorMsg: String = "Provide at least one of the following: instanceId, commitSha or buildVersion"
): String {
    if (groupId.isBlank()) {
        throw InvalidParameters("groupId cannot be empty or blank")
    }

    if (appId.isBlank()) {
        throw InvalidParameters("appId cannot be empty or blank")
    }

    if (instanceId.isNullOrBlank() && commitSha.isNullOrBlank() && buildVersion.isNullOrBlank()) {
        throw InvalidParameters(errorMsg)
    }

    return listOf(
        groupId,
        appId,
        listOf(buildVersion, commitSha, instanceId).first { !it.isNullOrBlank() }
    ).joinToString(BUILD_ID_SEPARATOR)
}

data class AppGroupIds(val groupId: String, val appId: String)

fun getAppAndGroupIdFromBuildId(buildId: String): AppGroupIds {
    val (groupId, appId, _) = buildId.split(BUILD_ID_SEPARATOR)
    return AppGroupIds(groupId, appId)
}
