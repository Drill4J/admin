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
    private val store = mutableMapOf<Pair<EtlContext, String>, EtlMetadata>()


    override suspend fun getMetadata(
        context: EtlContext,
        pipelineName: String,
    ): EtlMetadata? = store[context to pipelineName]

    override suspend fun saveMetadata(context: EtlContext, metadata: EtlMetadata) {
        store[context to metadata.pipelineName] = metadata
    }

    override suspend fun deleteMetadataByPipeline(context: EtlContext, pipelineName: String) {
        store.keys.remove(context to pipelineName)
    }

    override suspend fun getAllMetadata(context: EtlContext): List<EtlMetadata> =
        store.filter { it.key.first == context }.values.toList()

    override suspend fun accumulateMetadataByLoader(
        context: EtlContext,
        pipelineName: String,
        lastProcessedAt: Instant?,
        status: EtlStatus?,
        loadDuration: Long,
        rowsProcessed: Long,
        errorMessage: String?
    ) {
        val existing = store[context to pipelineName]
        if (existing == null) return
        store[context to pipelineName] = existing.copy(
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
        status: EtlStatus?,
        extractDuration: Long,
        errorMessage: String?
    ) {
        val existing = store[context to pipelineName]
        if (existing == null) return
        store[context to pipelineName] = existing.copy(
            lastExtractDuration = existing.lastExtractDuration + extractDuration,
            status = if (errorMessage != null) EtlStatus.FAILED else existing.status,
            errorMessage = errorMessage
        )
    }
}