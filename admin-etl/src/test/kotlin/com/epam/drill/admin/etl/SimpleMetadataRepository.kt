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

import java.time.Instant

class SimpleMetadataRepository : EtlMetadataRepository {
    private data class Key(val context: EtlContext, val pipelineName: String, val period: EtlPeriod)

    private val store = mutableMapOf<Key, EtlMetadata>()

    override suspend fun getMetadata(
        context: EtlContext,
        pipelineName: String,
        period: EtlPeriod,
    ): EtlMetadata? = store[Key(context, pipelineName, period)]

    override suspend fun saveMetadata(context: EtlContext, metadata: EtlMetadata) {
        store[Key(context, metadata.pipelineName, metadata.period)] = metadata
    }

    override suspend fun deleteMetadataByPipeline(context: EtlContext, pipelineName: String, period: EtlPeriod) {
        store.keys.remove(Key(context, pipelineName, period))
    }

    override suspend fun getAllMetadata(context: EtlContext, period: EtlPeriod): List<EtlMetadata> =
        store.filter { it.key.context == context && it.key.period == period }.values.toList()

    override suspend fun accumulateMetadataByLoader(
        context: EtlContext,
        pipelineName: String,
        period: EtlPeriod,
        lastProcessedAt: Instant?,
        status: EtlStatus?,
        loadDuration: Long,
        rowsProcessed: Long,
        errorMessage: String?
    ) {
        val key = Key(context, pipelineName, period)
        val existing = store[key] ?: return
        store[key] = existing.copy(
            lastProcessedAt = lastProcessedAt ?: existing.lastProcessedAt,
            lastLoadDuration = existing.lastLoadDuration + loadDuration,
            lastRowsProcessed = existing.lastRowsProcessed + rowsProcessed,
            status = status ?: (if (errorMessage != null) EtlStatus.FAILED else existing.status),
            errorMessage = errorMessage
        )
    }

    override suspend fun accumulateMetadataByExtractor(
        context: EtlContext,
        pipelineName: String,
        period: EtlPeriod,
        status: EtlStatus?,
        extractDuration: Long,
        errorMessage: String?
    ) {
        val key = Key(context, pipelineName, period)
        val existing = store[key] ?: return
        store[key] = existing.copy(
            lastExtractDuration = existing.lastExtractDuration + extractDuration,
            status = status ?: (if (errorMessage != null) EtlStatus.FAILED else existing.status),
            errorMessage = errorMessage
        )
    }

    override suspend fun getUnfinishedTargets(pipelineNames: Collection<String>): List<EtlRunTarget> {
        val unfinished = setOf(EtlStatus.EXTRACTING, EtlStatus.LOADING, EtlStatus.FAILED)
        return store.entries
            .filter { it.key.pipelineName in pipelineNames && it.value.status in unfinished && it.key.period.isBounded }
            .map { EtlRunTarget(it.key.context, it.key.period) }
            .distinct()
    }
}
