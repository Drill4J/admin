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
package com.epam.drill.admin.etl

data class EtlContext(
    val groupId: String,
    val appId: String? = null,
    val buildId: String? = null,
    val instanceId: String? = null,
    val testSessionId: String? = null,
    val testDefinitionId: String? = null,
    val testLaunchId: String? = null,
)

fun EtlContext.toMap(): Map<String, String?> = mapOf(
    "group_id" to groupId,
    "app_id" to appId,
    "build_id" to buildId,
    "instance_id" to instanceId,
    "test_session_id" to testSessionId,
    "test_definition_id" to testDefinitionId,
    "test_launch_id" to testLaunchId,
)