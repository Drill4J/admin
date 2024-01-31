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
