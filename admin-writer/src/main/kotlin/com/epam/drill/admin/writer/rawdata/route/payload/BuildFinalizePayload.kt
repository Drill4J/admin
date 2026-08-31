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
package com.epam.drill.admin.writer.rawdata.route.payload

import kotlinx.serialization.Serializable

/**
 * Payload sent by the agent once all build data (methods) has been submitted, requesting the
 * server to validate the integrity of the build.
 *
 * @param methodsCount total number of methods included in the build, as counted by the agent.
 * @param methodsChecksum combined checksum of all methods included in the build (see
 * `combineChecksumsCrc64` for the algorithm), as calculated by the agent.
 */
@Serializable
class BuildFinalizePayload(
    val groupId: String,
    val appId: String,
    val commitSha: String? = null,
    val buildVersion: String? = null,
    val instanceId: String? = null,
    val methodsCount: Int,
    val methodsChecksum: String,
)
