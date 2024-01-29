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
package com.epam.drill.plugins.test2code.common.api

import kotlinx.serialization.*

@Serializable
sealed class CoverMessage

@SerialName("INIT")
@Serializable
data class InitInfo(
    val classesCount: Int = 0,
    val message: String = "",
    @Deprecated(message = "the parameter is unused")
    val init: Boolean = true,
) : CoverMessage()

@SerialName("INIT_DATA_PART")
@Serializable
data class InitDataPart(val astEntities: List<AstEntity>) : CoverMessage()

@SerialName("INITIALIZED")
@Serializable
data class Initialized(val msg: String = "") : CoverMessage()

//TODO EPMDJ-10321 remove it after supporting on other agents
@SerialName("SCOPE_INITIALIZED")
@Serializable
@Deprecated("it is the old and unnecessary action")
data class ScopeInitialized(
    val id: String,
    val name: String,
    val prevId: String,
    val ts: Long,
) : CoverMessage()

@SerialName("SESSION_STARTED")
@Serializable
@Deprecated("Will be removed after migration agent-part")
data class SessionStarted(
    val sessionId: String,
    val testType: String,
    val isRealtime: Boolean = false,
    val isGlobal: Boolean = false,
    val ts: Long,
) : CoverMessage()

@SerialName("SESSION_CANCELLED")
@Serializable
data class SessionCancelled(val sessionId: String, val ts: Long) : CoverMessage()

@SerialName("SESSIONS_CANCELLED")
@Serializable
data class SessionsCancelled(val ids: List<String>, val ts: Long) : CoverMessage()

@SerialName("COVERAGE_DATA_PART")
@Serializable
data class CoverDataPart(val sessionId: String? = null, val data: List<ExecClassData>) : CoverMessage()

@SerialName("SESSION_CHANGED")
@Serializable
data class SessionChanged(val sessionId: String, val probeCount: Int) : CoverMessage()

@SerialName("SESSION_FINISHED")
@Serializable
data class SessionFinished(val sessionId: String, val ts: Long) : CoverMessage()

@SerialName("SESSIONS_FINISHED")
@Serializable
data class SessionsFinished(val ids: List<String>, val ts: Long) : CoverMessage()

@SerialName("SYNC_MESSAGE")
@Serializable
data class SyncMessage(val activeSessions: Set<String>) : CoverMessage()
