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
import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.UntypedRow
import com.epam.drill.admin.etl.config.EtlConfig
import kotlinx.coroutines.flow.Flow

/**
 * Entry point for the fluent ETL pipeline builder DSL.
 */
fun EtlConfig.pipeline(name: String): PipelineNameStep =
    PipelineNameStep(name = name, etlConfig = this)

class PipelineNameStep internal constructor(
    val name: String,
    internal val etlConfig: EtlConfig,
) {
    fun extractWith(extractor: DataExtractor<UntypedRow>): ExtractStep =
        ExtractStep(name = name, etlConfig = etlConfig, extractor = extractor)
}

class ExtractStep internal constructor(
    val name: String,
    internal val etlConfig: EtlConfig,
    val extractor: DataExtractor<UntypedRow>,
) {
    /**
     * Add a filter step that excludes rows for which [predicate] returns false.
     */
    fun filter(
        predicate: (UntypedRow) -> Boolean,
    ): TransformStep = TransformStep(
        name = name,
        etlConfig = etlConfig,
        extractor = extractor,
        transformer = UntypedFilterTransformer(
            name = name,
            metrics = etlConfig.metrics,
            predicate = predicate,
        )
    )

    /**
     * Add an aggregation step that groups rows by [groupKeys].
     */
    fun aggregateBy(
        vararg groupKeys: String,
        aggregate: (current: UntypedRow, next: UntypedRow) -> UntypedRow,
    ): TransformStep = TransformStep(
        name = name,
        etlConfig = etlConfig,
        extractor = extractor,
        transformer = UntypedAggregationTransformer(
            name = name,
            bufferSize = etlConfig.transformationBufferSize,
            loggingFrequency = etlConfig.loggingFrequency,
            metrics = etlConfig.metrics,
            groupKeys = groupKeys.toList(),
            aggregate = aggregate,
        )
    )

    /**
     * Add a custom transformation step that applies the provided [transformer] to the extracted rows.
     */
    fun transformWith(
        transformer: DataTransformer<UntypedRow, UntypedRow>,
    ): TransformStep = TransformStep(
        name = name,
        etlConfig = etlConfig,
        extractor = extractor,
        transformer = transformer,
    )

    /**
     * Attach a loader â€” terminates the pipeline with no transformation.
     */
    fun loadWith(
        loader: DataLoader<UntypedRow>,
    ): EtlPipelineImpl<UntypedRow, UntypedRow> =
        EtlPipelineImpl(
            name = name,
            extractor = extractor,
            transformer = NoOpTransformer,
            loader = loader,
            metrics = etlConfig.metrics,
        )
}

class TransformStep internal constructor(
    val name: String,
    internal val etlConfig: EtlConfig,
    val extractor: DataExtractor<UntypedRow>,
    val transformer: DataTransformer<UntypedRow, UntypedRow>,
) {
    fun filter(
        predicate: (UntypedRow) -> Boolean,
    ): TransformStep = append(
        UntypedFilterTransformer(
            name = name,
            metrics = etlConfig.metrics,
            predicate = predicate,
        )
    )

    /**
     * Chain an additional aggregation step.
     */
    fun aggregateBy(
        vararg groupKeys: String,
        aggregate: (current: UntypedRow, next: UntypedRow) -> UntypedRow,
    ): TransformStep = append(
        UntypedAggregationTransformer(
            name = name,
            bufferSize = etlConfig.transformationBufferSize,
            loggingFrequency = etlConfig.loggingFrequency,
            metrics = etlConfig.metrics,
            groupKeys = groupKeys.toList(),
            aggregate = aggregate,
        )
    )

    /**
     * Chain an additional custom transformation step.
     */
    fun transformWith(
        transformer: DataTransformer<UntypedRow, UntypedRow>,
    ): TransformStep = append(transformer)

    /**
     * Attach a loader to the end of the transformation chain.
     */
    fun loadWith(
        loader: DataLoader<UntypedRow>,
    ): EtlPipelineImpl<UntypedRow, UntypedRow> =
        EtlPipelineImpl(
            name = name,
            extractor = extractor,
            transformer = transformer,
            loader = loader,
            metrics = etlConfig.metrics,
        )

    private fun append(next: DataTransformer<UntypedRow, UntypedRow>): TransformStep =
        TransformStep(
            name = name,
            etlConfig = etlConfig,
            extractor = extractor,
            transformer = SequencedTransformer(transformer, next),
        )
}

/**
 * Composes two [DataTransformer]s in sequence, so that the output of the first one is fed into the second one.
 */
internal class SequencedTransformer<T : EtlRow, M : EtlRow, R : EtlRow>(
    private val first: DataTransformer<T, M>,
    private val second: DataTransformer<M, R>
) : DataTransformer<T, R> {
    override val name: String = "${first.name}+${second.name}"
    override suspend fun transform(context: EtlContext, collector: Flow<T>): Flow<R> =
        second.transform(context, first.transform(context, collector))
}

/**
 * No-op transformer used when [ExtractStep.loadWith] is called without any prior transform step.
 */
internal object NoOpTransformer : DataTransformer<UntypedRow, UntypedRow> {
    override val name: String = "identity"
    override suspend fun transform(
        context: EtlContext,
        collector: Flow<UntypedRow>,
    ): Flow<UntypedRow> = collector
}

