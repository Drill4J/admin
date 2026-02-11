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

import kotlinx.coroutines.flow.Flow

interface DataTransformer<in T: EtlRow, out R: EtlRow> {
    val name: String
    suspend fun transform(
        groupId: String,
        collector: Flow<T>,
    ): Flow<R>
}

class NopTransformer<T: EtlRow> : DataTransformer<T, T> {
    override val name: String = "nop-transformer"
    override suspend fun transform(
        groupId: String,
        collector: Flow<T>,
    ): Flow<T> = collector
}

val untypedNopTransformer = NopTransformer<UntypedRow>()
