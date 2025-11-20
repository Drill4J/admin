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

open class BatchResult(
    open val success: Boolean,
    open val duration: Long? = null,
    open val errorMessage: String? = null
)

data class EtlLoadingResult(
    val lastProcessedAt: Instant? = null,
    val processedRows: Int = 0,
    override val success: Boolean,
    override val duration: Long? = null,
    override val errorMessage: String? = null
): BatchResult(success, duration, errorMessage), Comparable<EtlLoadingResult> {
    companion object {
        val EMPTY = EtlLoadingResult(
            success = true
        )
    }

    operator fun plus(other: EtlLoadingResult): EtlLoadingResult {
        val success = this.success && other.success
        val lastProcessedAt = if (success) {
            other.lastProcessedAt
        } else {
            this.lastProcessedAt
        }
        return EtlLoadingResult(
            success = success,
            lastProcessedAt = lastProcessedAt,
            processedRows = this.processedRows + other.processedRows,
            duration = listOfNotNull(this.duration, other.duration).sum(),
            errorMessage = listOfNotNull(this.errorMessage, other.errorMessage).joinToString("; ").ifEmpty { null }
        )
    }

    override fun compareTo(other: EtlLoadingResult): Int {
        if (this.success != other.success) {
            return if (this.success) 1 else -1
        }
        if (this.lastProcessedAt != other.lastProcessedAt) {
            return when {
                this.lastProcessedAt == null -> -1
                other.lastProcessedAt == null -> 1
                else -> this.lastProcessedAt.compareTo(other.lastProcessedAt)
            }
        }
        if (this.processedRows != other.processedRows) {
            return this.processedRows.compareTo(other.processedRows)
        }
        return 0
    }
}