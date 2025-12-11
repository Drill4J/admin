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

interface EtlMetadataRepository {
    suspend fun getAllMetadata(groupId: String): List<EtlMetadata>
    suspend fun getAllMetadataByExtractor(groupId: String, pipelineName: String, extractorName: String): List<EtlMetadata>
    suspend fun getMetadata(groupId: String, pipelineName: String, extractorName: String, loaderName: String): EtlMetadata?
    suspend fun saveMetadata(metadata: EtlMetadata)
    suspend fun accumulateMetadata(metadata: EtlMetadata)
    suspend fun deleteMetadataByPipeline(groupId: String, pipelineName: String)
    suspend fun accumulateMetadataDurationByExtractor(groupId: String, pipelineName: String, extractorName: String, duration: Long)
}

