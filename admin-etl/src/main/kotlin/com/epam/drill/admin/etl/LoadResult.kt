package com.epam.drill.admin.etl

import java.time.Instant

data class LoadResult(
    val lastProcessedAt: Instant? = null,
    val processedRows: Int = 0,
    val success: Boolean,
    val duration: Long? = null,
    val errorMessage: String? = null
): Comparable<LoadResult> {
    companion object {
        val EMPTY = LoadResult(
            success = true
        )
    }

    operator fun plus(other: LoadResult): LoadResult {
        val success = this.success && other.success
        val lastProcessedAt = if (success) {
            other.lastProcessedAt
        } else {
            this.lastProcessedAt
        }
        return LoadResult(
            success = success,
            lastProcessedAt = lastProcessedAt,
            processedRows = this.processedRows + other.processedRows,
            duration = listOfNotNull(this.duration, other.duration).sum(),
            errorMessage = listOfNotNull(this.errorMessage, other.errorMessage).joinToString("; ").ifEmpty { null }
        )
    }

    override fun compareTo(other: LoadResult): Int {
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