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
package com.epam.drill.admin.etl.service

import com.epam.drill.admin.etl.EtlDailyStatusRow
import com.epam.drill.admin.etl.model.EtlJobView
import java.time.Instant
import java.time.LocalDate

/**
 * Service for ETL operations.
 */
interface EtlService {

    /**
     * Refreshes the ETL data.
     * If a job is already running, it will be skipped.
     *
     * @param groupId The group ID to refresh the ETL data for.
     */
    suspend fun refresh(groupId: String? = null)

    /**
     * Forces a refresh of the ETL data.
     * If a job is already running, it will be waited for completion before starting a new one.
     *
     * @param groupId The group ID to refresh the ETL data for.
     * @param snapshotTimestamp An optional timestamp to use for the refresh snapshot.
     * @return The timestamp of the refresh operation.
     */
    suspend fun forceRefresh(groupId: String? = null, snapshotTimestamp: Instant? = null): Instant

    /**
     * Finds unplanned/unloaded days (within the group's configured history window), combines
     * them into contiguous periods, schedules jobs for those periods (bounded by the available
     * worker budget) and runs the scheduled jobs.
     */
    suspend fun scheduleUnloadedDays(groupId: String? = null)

    /** Force rerun for `[from, to]`: cancels overlapping jobs, schedules and runs new ones. */
    suspend fun rerunDateRange(groupId: String? = null, from: LocalDate?, to: LocalDate?)

    /** Force rerun of the whole history. */
    suspend fun rerunAllData(groupId: String? = null)

    /** Force rerun of the `(today, today)` period. */
    suspend fun rerunToday(groupId: String? = null)

    /** Resumes/(re)starts idle or expired-lease jobs, bounded by the available worker budget. */
    suspend fun runIdleJobs(groupId: String? = null)

    /** Per-day ETL status within `[from, to]` for [groupId] (worst status across orchestrators wins). */
    suspend fun getDailyStatuses(groupId: String, from: LocalDate?, to: LocalDate?): List<EtlDailyStatusRow>

    /** The furthest timestamp processed so far for [groupId] (the minimum across orchestrators), or null. */
    suspend fun getLastProcessedTimestamp(groupId: String): Instant?

    suspend fun loadTestDefinitionCoverage(
        groupId: String, testSessionId: String, testDefinitionId: String,
        snapshotTimestamp: Instant? = null,
    )

    suspend fun getActiveJobs(groupId: String?, from: LocalDate?, to: LocalDate?): List<EtlJobView>
}