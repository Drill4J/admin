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
package com.epam.drill.admin.metrics.etl

import com.epam.drill.admin.etl.DataTransformer
import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.UntypedRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.postgresql.jdbc.PgArray
import java.time.Instant
import kotlin.collections.filterIsInstance

class TestTagsFlatteningTransformer(
    override val name: String,
): DataTransformer<UntypedRow, UntypedRow> {

    override suspend fun transform(
        context: EtlContext,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        collector: Flow<UntypedRow>,
    ): Flow<UntypedRow> = flow {
        collector.collect { row ->
            val tags: List<String>? = when (row["test_tags"]) {
                is java.sql.Array -> ((row["test_tags"] as java.sql.Array).array as Array<*>).filterIsInstance<String>()
                is Collection<*> -> (row["test_tags"] as Collection<*>).filterIsInstance<String>()
                else -> null
            }
            if (tags.isNullOrEmpty()) {
                emit(row)
            } else {
                tags.forEach { tag ->
                    emit(UntypedRow(row.timestamp, (row as Map<String, Any?>) + ("test_tag" to tag)))
                }
            }
        }
    }
}