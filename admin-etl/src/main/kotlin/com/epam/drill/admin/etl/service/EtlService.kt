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
package com.epam.drill.admin.etl.service

import com.epam.drill.admin.etl.EtlProcessingResult
import java.time.Instant
import java.time.LocalDate

interface EtlService {
    suspend fun refresh(
        groupId: String? = null,
        reset: Boolean = false,
        initTimestamp: Instant? = null,
        finalTimestamp: Instant? = null,
        fromDay: LocalDate? = null,
        toDay: LocalDate? = null,
        chunkDays: Int? = null,
        skipIfLocked: Boolean = false,
    ): List<EtlProcessingResult>

    /**
     * Resumes interrupted/failed bounded-period reruns for the given orchestrator (or all
     * orchestrators when [etlName] is null). Used by the scheduler to continue back-fills
     * after a restart.
     */
    suspend fun resumeUnfinished(): List<EtlProcessingResult>

    suspend fun getRefreshStatus(groupId: String): Map<String, Any?>
}