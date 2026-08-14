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

import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlJob
import com.epam.drill.admin.etl.EtlJobResult
import com.epam.drill.admin.etl.EtlJobStatus
import com.epam.drill.admin.etl.EtlOrchestrator
import com.epam.drill.admin.etl.EtlPeriod
import com.epam.drill.admin.etl.EtlPipeline
import com.epam.drill.admin.etl.SimpleEtlJobsRepository
import com.epam.drill.admin.etl.exception.LockAcquisitionException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EtlLauncherImplTest {

    /**
     * Mock [EtlOrchestrator] that records every [run]/[rerun] invocation and lets tests control
     * the returned [EtlJobResult] via [onRun]/[onRerun] callbacks. Mimics [EtlOrchestratorImpl]'s
     * responsibility of updating [jobsRepository] (e.g. marking the job COMPLETED) so that
     * launcher-level lock/status bookkeeping can be verified end-to-end.
     */
    private class TestEtlOrchestrator(
        private val jobsRepository: SimpleEtlJobsRepository,
        override val name: String = "test-etl",
        override val pipelines: List<EtlPipeline<*, *>> = emptyList(),
        private val onRun: suspend (EtlJob, String, Instant?) -> EtlJobResult = { job, workerId, _ ->
            EtlJobResult(
                job = job,
                status = EtlJobStatus.COMPLETED,
                processedUntilTimestamp = Instant.now(),
                workerId = workerId,
            )
        },
        private val onRerun: suspend (EtlJob, String, Boolean) -> EtlJobResult = { job, workerId, _ ->
            EtlJobResult(
                job = job,
                status = EtlJobStatus.COMPLETED,
                processedUntilTimestamp = Instant.now(),
                workerId = workerId,
            )
        },
    ) : EtlOrchestrator {
        val runCalls = Collections.synchronizedList(mutableListOf<Triple<EtlJob, String, Instant?>>())
        val rerunCalls = Collections.synchronizedList(mutableListOf<Triple<EtlJob, String, Boolean>>())

        override suspend fun run(job: EtlJob, workerId: String, snapshotTimestamp: Instant?): EtlJobResult {
            runCalls += Triple(job, workerId, snapshotTimestamp)
            return onRun(job, workerId, snapshotTimestamp).also { applyToRepository(it, workerId) }
        }

        override suspend fun rerun(job: EtlJob, workerId: String, withDataDeletion: Boolean): EtlJobResult {
            rerunCalls += Triple(job, workerId, withDataDeletion)
            return onRerun(job, workerId, withDataDeletion).also { applyToRepository(it, workerId) }
        }

        private suspend fun applyToRepository(result: EtlJobResult, workerId: String) {
            when (result.status) {
                EtlJobStatus.COMPLETED -> jobsRepository.markCompleted(result.job, workerId, result.processedUntilTimestamp ?: result.job.period.sinceTimestamp ?: Instant.EPOCH)
                EtlJobStatus.IDLE -> jobsRepository.markIdle(result.job, workerId, result.processedUntilTimestamp ?: result.job.period.sinceTimestamp ?: Instant.EPOCH)
                EtlJobStatus.ERROR -> jobsRepository.markError(result.job, workerId, result.errorMessage)
                EtlJobStatus.CANCELLED -> jobsRepository.markCancelled(result.job, workerId)
                EtlJobStatus.RUNNING -> Unit
            }
        }
    }

    private val context = EtlContext(groupId = "g1")

    @Test
    fun `run acquires lock, delegates to orchestrator and completes the job`() = runBlocking {
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = TestEtlOrchestrator(jobsRepository)
        val launcher = EtlLauncherImpl(orchestrator, jobsRepository)

        val period = EtlPeriod.UNBOUNDED
        val job = jobsRepository.scheduleJob(orchestrator.name, context, period)
            ?: error("Failed to schedule job")

        val result = launcher.run(job)

        assertEquals(1, orchestrator.runCalls.size, "Orchestrator must be invoked exactly once")
        assertEquals(EtlJobStatus.COMPLETED, result.status)

        val activeJobs = jobsRepository.getActiveJobs(orchestrator.name, context, period)
        assertTrue(activeJobs.isEmpty(), "Completed job must no longer be active")
    }

    @Test
    fun `run skips already running job and returns its status without invoking orchestrator`() = runBlocking {
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = TestEtlOrchestrator(jobsRepository)
        val launcher = EtlLauncherImpl(orchestrator, jobsRepository)

        val period = EtlPeriod.UNBOUNDED
        val job = jobsRepository.scheduleJob(orchestrator.name, context, period)
            ?: error("Failed to schedule job")
        val locked = jobsRepository.lockJob(job, "other-worker", leaseSeconds = 180)
        assertTrue(locked, "Precondition: job must be locked by another worker")

        val result = launcher.run(job, skipIfRunning = true)

        assertEquals(EtlJobStatus.RUNNING, result.status)
        assertEquals("other-worker", result.workerId)
        assertTrue(orchestrator.runCalls.isEmpty(), "Orchestrator must not be invoked for a skipped job")
    }

    @Test
    fun `run retries acquiring the lock until it is released and then succeeds`() = runBlocking {
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = TestEtlOrchestrator(jobsRepository)
        val launcher = EtlLauncherImpl(orchestrator, jobsRepository, lockRetryDelay = 20L)

        val period = EtlPeriod.UNBOUNDED
        val job = jobsRepository.scheduleJob(orchestrator.name, context, period)
            ?: error("Failed to schedule job")
        jobsRepository.lockJob(job, "other-worker", leaseSeconds = 180)

        coroutineScope {
            launch {
                delay(60L)
                jobsRepository.markIdle(job, "other-worker", Instant.now())
            }
            val result = launcher.run(job, skipIfRunning = false)
            assertEquals(EtlJobStatus.COMPLETED, result.status)
        }

        assertEquals(1, orchestrator.runCalls.size)
    }

    @Test
    fun `run throws when the lock cannot be acquired after all retry attempts`() = runBlocking {
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = TestEtlOrchestrator(jobsRepository)
        val launcher = EtlLauncherImpl(orchestrator, jobsRepository, lockRetryDelay = 1L)

        val period = EtlPeriod.UNBOUNDED
        val job = jobsRepository.scheduleJob(orchestrator.name, context, period)
            ?: error("Failed to schedule job")
        jobsRepository.lockJob(job, "other-worker", leaseSeconds = 180)

        assertFailsWith<LockAcquisitionException> {
            launcher.run(job, skipIfRunning = false)
        }
        assertTrue(orchestrator.runCalls.isEmpty())
    }

    @Test
    fun `schedule splits a bounded period into up to workers chunks covering it fully`() = runBlocking {
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = TestEtlOrchestrator(jobsRepository)
        val launcher = EtlLauncherImpl(orchestrator, jobsRepository)

        val from = LocalDate.of(2024, 1, 1)
        val to = from.plusDays(9) // 10 days total
        val period = EtlPeriod(from, to)

        val jobs = launcher.schedule(context, period, workers = 3)

        assertEquals(3, jobs.size)
        // chunks must be contiguous and cover the whole period without gaps or overlaps
        val sorted = jobs.sortedBy { it.period.from }
        assertEquals(from, sorted.first().period.from)
        assertEquals(to, sorted.last().period.to)
        for (i in 0 until sorted.size - 1) {
            assertEquals(sorted[i].period.to!!.plusDays(1), sorted[i + 1].period.from)
        }
    }

    @Test
    fun `schedule returns a single job for an unbounded period regardless of worker count`() = runBlocking {
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = TestEtlOrchestrator(jobsRepository)
        val launcher = EtlLauncherImpl(orchestrator, jobsRepository)

        val jobs = launcher.schedule(context, EtlPeriod.UNBOUNDED, workers = 5)

        assertEquals(1, jobs.size)
        assertEquals(EtlPeriod.UNBOUNDED, jobs.single().period)
    }

    @Test
    fun `schedule fails fast when a chunk overlaps an already active job`(): Unit = runBlocking {
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = TestEtlOrchestrator(jobsRepository)
        val launcher = EtlLauncherImpl(orchestrator, jobsRepository)

        val from = LocalDate.of(2024, 1, 1)
        val to = from.plusDays(3) // 4 days -> 2 chunks of 2 days with workers=2
        val period = EtlPeriod(from, to)

        // Pre-schedule a job overlapping the first chunk to force the exclude-constraint failure
        jobsRepository.scheduleJob(orchestrator.name, context, EtlPeriod(from, from))

        assertFailsWith<IllegalStateException> {
            launcher.schedule(context, period, workers = 2)
        }
    }

    @Test
    fun `resume runs all resumable jobs and returns their results`() = runBlocking {
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = TestEtlOrchestrator(jobsRepository)
        val launcher = EtlLauncherImpl(orchestrator, jobsRepository)

        val from = LocalDate.of(2024, 1, 1)
        val period = EtlPeriod(from, from.plusDays(5))
        val jobs = launcher.schedule(context, period, workers = 2)
        assertEquals(2, jobs.size)

        val results = launcher.resume(context, period)

        assertEquals(2, results.size)
        assertTrue(results.all { it.status == EtlJobStatus.COMPLETED })
        assertEquals(2, orchestrator.runCalls.size)
    }

    @Test
    fun `resume returns empty list when there is nothing to resume`() = runBlocking {
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = TestEtlOrchestrator(jobsRepository)
        val launcher = EtlLauncherImpl(orchestrator, jobsRepository)

        val results = launcher.resume(context, EtlPeriod.UNBOUNDED)

        assertTrue(results.isEmpty())
        assertTrue(orchestrator.runCalls.isEmpty())
    }

    @Test
    fun `cancel delegates to jobsRepository and cancels active jobs`() = runBlocking {
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = TestEtlOrchestrator(jobsRepository)
        val launcher = EtlLauncherImpl(orchestrator, jobsRepository)

        val period = EtlPeriod.UNBOUNDED
        val job = jobsRepository.scheduleJob(orchestrator.name, context, period)
            ?: error("Failed to schedule job")
        jobsRepository.lockJob(job, "worker-1", leaseSeconds = 180)

        val results = launcher.cancel(context, period)

        assertEquals(1, results.size)
        assertEquals(EtlJobStatus.CANCELLED, results.single().status)
        assertNull(jobsRepository.getActiveJob(job))
    }

    @Test
    fun `rerun cancels active jobs, reschedules and reruns via orchestrator`() = runBlocking {
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = TestEtlOrchestrator(jobsRepository)
        val launcher = EtlLauncherImpl(orchestrator, jobsRepository)

        val from = LocalDate.of(2024, 1, 1)
        val period = EtlPeriod(from, from.plusDays(2))

        // Simulate a job already running for this period that must be cancelled by rerun
        val existingJob = jobsRepository.scheduleJob(orchestrator.name, context, period)
            ?: error("Failed to schedule job")
        jobsRepository.lockJob(existingJob, "stale-worker", leaseSeconds = 180)

        val results = launcher.rerun(context, period, workers = 1, withDataDeletion = true)

        assertEquals(1, results.size)
        assertEquals(EtlJobStatus.COMPLETED, results.single().status)
        assertEquals(1, orchestrator.rerunCalls.size)
        assertEquals(true, orchestrator.rerunCalls.single().third)
        assertTrue(orchestrator.runCalls.isEmpty(), "rerun must call orchestrator.rerun, not orchestrator.run")

        // the stale job must have been cancelled, so the only active job left is the new (completed) one
        val activeJobs = jobsRepository.getActiveJobs(orchestrator.name, context, period)
        assertTrue(activeJobs.isEmpty())
    }
}
