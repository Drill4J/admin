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
package com.epam.drill.admin.writer.rawdata.entity

class Method(
    val groupId: String,
    val appId: String,
    val id: String,
    val buildId: String,
    val classname: String,
    val name: String,
    val params: String,
    val returnType: String,
    val probesCount: Int,
    val probesStartPos: Int,
    val bodyChecksum: String,
    val signature: String,
    val annotations: Map<String, List<String>>?,
    val classAnnotations: Map<String, List<String>>?
)