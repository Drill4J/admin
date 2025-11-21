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

data class EtlLoadingResult(
    val lastProcessedAt: Instant? = null,
    val processedRows: Int = 0,
    val status: EtlStatus,
    val duration: Long? = null,
    val errorMessage: String? = null
) : Comparable<EtlLoadingResult> {
    companion object {
        val EMPTY = EtlLoadingResult(
            status = EtlStatus.STARTING
        )
    }

    val isFailed
        get() = status == EtlStatus.FAILED

    operator fun plus(other: EtlLoadingResult): EtlLoadingResult {
        val failed = this.isFailed || other.isFailed
        return EtlLoadingResult(
            status = if (!failed) other.status else EtlStatus.FAILED,
            lastProcessedAt = if (!failed) other.lastProcessedAt else this.lastProcessedAt,
            processedRows = this.processedRows + other.processedRows,
            duration = listOfNotNull(this.duration, other.duration).sum(),
            errorMessage = listOfNotNull(this.errorMessage, other.errorMessage).joinToString("; ").ifEmpty { null }
        )
    }

    override fun compareTo(other: EtlLoadingResult): Int {
        if (this.isFailed || other.isFailed) {
            return if (this.isFailed) -1 else if (other.isFailed) 1 else 0
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