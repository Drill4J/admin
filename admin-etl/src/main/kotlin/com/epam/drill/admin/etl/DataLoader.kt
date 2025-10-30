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

import java.time.Instant

interface DataLoader<T> {
    val name: String
    suspend fun load(
        data: Sequence<T>,
        batchSize: Int = 1000
    ): LoadResult

    data class LoadResult(
        val lastProcessedAt: Instant? = null,
        val processedRows: Int = 0,
        val success: Boolean,
        val errorMessage: String? = null
    ) {
        companion object {
            val EMPTY = LoadResult(
                success = true
            )
        }

        operator fun plus(other: LoadResult): LoadResult {
            val success = this.success && other.success
            val lastProcessedAt = if (success) {
                max(this.lastProcessedAt, other.lastProcessedAt)
            } else {
                min(this.lastProcessedAt, other.lastProcessedAt)
            }
            return LoadResult(
                lastProcessedAt = lastProcessedAt,
                processedRows = this.processedRows + other.processedRows,
                success = success,
                errorMessage = listOfNotNull(this.errorMessage, other.errorMessage).joinToString("; ").ifEmpty { null }
            )
        }
    }
}

private fun min(a: Instant?, b: Instant?): Instant? {
    return when {
        a == null -> b
        b == null -> a
        else -> if (a < b) a else b
    }
}

private fun max(a: Instant?, b: Instant?): Instant? {
    return when {
        a == null -> b
        b == null -> a
        else -> if (a > b) a else b
    }
}