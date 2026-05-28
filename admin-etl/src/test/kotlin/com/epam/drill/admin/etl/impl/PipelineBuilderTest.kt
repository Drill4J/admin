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

import com.epam.drill.admin.etl.DataExtractor
import com.epam.drill.admin.etl.DataLoader
import com.epam.drill.admin.etl.DataTransformer
import com.epam.drill.admin.etl.EtlExtractingResult
import com.epam.drill.admin.etl.EtlLoadingResult
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.UntypedRow
import com.epam.drill.admin.etl.config.EtlConfig
import com.epam.drill.admin.etl.config.EtlMeter
import io.ktor.server.config.MapApplicationConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class ExtractStepTest {

    @Test
    fun `loadWith delivers all rows to the loader`() {
        val loader = PbCapturingLoader("loader")
        val rows = listOf(pbRow(1), pbRow(2), pbRow(3))

        val pipeline = pbCfg.pipeline("t")
            .extractWith(PbFixedExtractor(rows))
            .loadWith(loader)

        pbExecute(pipeline)

        assertEquals(3, loader.received.size)
    }

    @Test
    fun `filter on ExtractStep keeps only matching rows`() {
        val loader = PbCapturingLoader("loader")
        val rows = listOf(pbRow(1), pbRow(2), pbRow(3), pbRow(4))

        val pipeline = pbCfg.pipeline("t")
            .extractWith(PbFixedExtractor(rows))
            .filter { (it["value"] as Int) % 2 == 0 }
            .loadWith(loader)

        pbExecute(pipeline)

        assertEquals(2, loader.received.size)
        assertTrue(loader.received.all { (it["value"] as Int) % 2 == 0 })
    }

    @Test
    fun `transformWith on ExtractStep applies the transformer before loading`() {
        val loader = PbCapturingLoader("loader")
        val rows = listOf(pbRow(10), pbRow(20))

        val pipeline = pbCfg.pipeline("t")
            .extractWith(PbFixedExtractor(rows))
            .transformWith(PbPassThroughTransformer())
            .loadWith(loader)

        pbExecute(pipeline)

        assertEquals(2, loader.received.size)
        assertEquals(listOf(10, 20), loader.received.map { it["value"] })
    }

    @Test
    fun `aggregateBy on ExtractStep sums values for the same key`() {
        val loader = PbCapturingLoader("loader")
        val rows = listOf(
            UntypedRow(Instant.now(), mapOf("k" to "A", "v" to 1)),
            UntypedRow(Instant.now(), mapOf("k" to "A", "v" to 2)),
            UntypedRow(Instant.now(), mapOf("k" to "B", "v" to 5)),
        )

        val pipeline = pbCfg.pipeline("t")
            .extractWith(PbFixedExtractor(rows))
            .aggregateBy("k") { current, next ->
                UntypedRow(
                    next.timestamp,
                    mapOf("k" to current["k"], "v" to (current["v"] as Int) + (next["v"] as Int))
                )
            }
            .loadWith(loader)

        pbExecute(pipeline)

        assertEquals(2, loader.received.size)
        val sumA = loader.received.first { it["k"] == "A" }["v"] as Int
        assertEquals(3, sumA)
    }
}

class TransformStepTest {

    @Test
    fun `filter on TransformStep narrows rows after the transformer`() {
        val loader = PbCapturingLoader("loader")
        val rows = listOf(pbRow(1), pbRow(2), pbRow(3), pbRow(4), pbRow(5))

        val pipeline = pbCfg.pipeline("t")
            .extractWith(PbFixedExtractor(rows))
            .transformWith(PbPassThroughTransformer())
            .filter { (it["value"] as Int) > 3 }
            .loadWith(loader)

        pbExecute(pipeline)

        assertEquals(2, loader.received.size)
        assertTrue(loader.received.all { (it["value"] as Int) > 3 })
    }

    @Test
    fun `chained filter calls on TransformStep compose with logical AND semantics`() {
        val loader = PbCapturingLoader("loader")
        val rows = (1..6).map { pbRow(it) }

        val pipeline = pbCfg.pipeline("t")
            .extractWith(PbFixedExtractor(rows))
            .filter { (it["value"] as Int) > 2 }
            .filter { (it["value"] as Int) < 5 }
            .loadWith(loader)

        pbExecute(pipeline)

        assertEquals(2, loader.received.size)
        assertTrue(loader.received.all { (it["value"] as Int) in 3..4 })
    }

    @Test
    fun `transformWith on TransformStep chains transformers correctly`() {
        val loader = PbCapturingLoader("loader")
        val rows = listOf(pbRow(1), pbRow(2), pbRow(3))

        val pipeline = pbCfg.pipeline("t")
            .extractWith(PbFixedExtractor(rows))
            .transformWith(PbPassThroughTransformer("t1"))
            .transformWith(PbPassThroughTransformer("t2"))
            .loadWith(loader)

        pbExecute(pipeline)

        assertEquals(3, loader.received.size)
    }

    @Test
    fun `aggregateBy on TransformStep aggregates after a prior transform`() {
        val loader = PbCapturingLoader("loader")
        val rows = listOf(
            UntypedRow(Instant.now(), mapOf("k" to "X", "v" to 10)),
            UntypedRow(Instant.now(), mapOf("k" to "X", "v" to 20)),
        )

        val pipeline = pbCfg.pipeline("t")
            .extractWith(PbFixedExtractor(rows))
            .transformWith(PbPassThroughTransformer())
            .aggregateBy("k") { cur, nxt ->
                UntypedRow(
                    nxt.timestamp,
                    mapOf("k" to cur["k"], "v" to (cur["v"] as Int) + (nxt["v"] as Int))
                )
            }
            .loadWith(loader)

        pbExecute(pipeline)

        assertEquals(1, loader.received.size)
        assertEquals(30, loader.received[0]["v"] as Int)
    }

    @Test
    fun `loadWith on TransformStep produces a pipeline with a single loader`() {
        val loader = PbCapturingLoader("loader")
        val pipeline = pbCfg.pipeline("t")
            .extractWith(PbFixedExtractor(emptyList()))
            .transformWith(PbPassThroughTransformer())
            .loadWith(loader)

        assertNotNull(pipeline.loader)
        assertEquals(loader.name, pipeline.loader.name)
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private val pbCfg = EtlConfig(
    config = MapApplicationConfig(),
    metrics = EtlMeter(SimpleMeterRegistry()),
)

private fun pbRow(value: Any?, key: String = "value", ts: Instant = Instant.now()) =
    UntypedRow(ts, mapOf(key to value))

private fun pbExecute(
    pipeline: com.epam.drill.admin.etl.EtlPipeline<UntypedRow, UntypedRow>,
) = runBlocking {
    // Use an empty flow â€” tests only verify pipeline structure and transformation logic via pbExecute
    // through the orchestrator in integration tests; here we call execute with a pre-built extractor flow.
    val rows = mutableListOf<UntypedRow>()
    // Re-extract via the pipeline's extractor into a simple list, then replay as flow
    pipeline.extractor.extract("g1", Instant.EPOCH, Instant.now(), { rows.add(it) }, {})
    val extractedFlow = kotlinx.coroutines.flow.flow<UntypedRow> { rows.forEach { emit(it) } }
    pipeline.execute(
        groupId = "g1",
        sinceTimestamp = Instant.EPOCH,
        untilTimestamp = Instant.now(),
        extractedFlow = extractedFlow,
    )
}

private class PbFixedExtractor(
    private val rows: List<UntypedRow>,
    override val name: String = "fixed",
) : DataExtractor<UntypedRow> {
    override suspend fun extract(
        groupId: String,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        emitter: FlowCollector<UntypedRow>,
        onExtractingProgress: suspend (EtlExtractingResult) -> Unit,
    ) = rows.forEach { emitter.emit(it) }
}

private class PbCapturingLoader(override val name: String) : DataLoader<UntypedRow> {
    val received = mutableListOf<UntypedRow>()

    override suspend fun load(
        groupId: String,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        collector: Flow<UntypedRow>,
        onLoadingProgress: suspend (EtlLoadingResult) -> Unit,
        onStatusChanged: suspend (EtlStatus) -> Unit,
    ): EtlLoadingResult {
        collector.collect { received.add(it) }
        return EtlLoadingResult(lastProcessedAt = untilTimestamp, processedRows = received.size.toLong())
    }

    override suspend fun deleteAll(groupId: String) = received.clear()
}

private class PbPassThroughTransformer(override val name: String = "pass") :
    DataTransformer<UntypedRow, UntypedRow> {
    override suspend fun transform(groupId: String, collector: Flow<UntypedRow>): Flow<UntypedRow> = flow {
        collector.collect { emit(it) }
    }
}


