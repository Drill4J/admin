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

import com.epam.drill.admin.common.service.generateBuildId
import kotlinx.serialization.Serializable

@Serializable
open class Build(
    open val groupId: String,
    open val appId: String,
    val instanceId: String? = null,
    val commitSha: String? = null,
    val buildVersion: String? = null,
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
    open val errorMessage = "Provide at least one of the following: instanceId, commitSha or buildVersion"
}