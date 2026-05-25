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
package com.epam.drill.admin.etl.impl

import com.epam.drill.admin.etl.DataTransformer
import com.epam.drill.admin.etl.EtlRow
import kotlinx.coroutines.flow.Flow

/**
 * Composes two [DataTransformer]s in sequence, so that the output of the first one is fed into the second one.
 */
class SequencedTransformer<T : EtlRow, M : EtlRow, R : EtlRow>(
    private val first: DataTransformer<T, M>,
    private val second: DataTransformer<M, R>
) : DataTransformer<T, R> {
    override val name: String = "${first.name}+${second.name}"
    override suspend fun transform(groupId: String, collector: Flow<T>): Flow<R> =
        second.transform(groupId, first.transform(groupId, collector))
}