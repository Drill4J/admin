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
     * Attach a loader
     */
    fun loadWith(
        loader: DataLoader<UntypedRow>,
        bufferSize: Int = etlConfig.bufferSize,
    ): EtlPipelineImpl<UntypedRow, UntypedRow> =
        EtlPipelineImpl(
            name = name,
            extractor = extractor,
            loaders = listOf(NoOpTransformer to loader),
            bufferSize = bufferSize,
            metrics = etlConfig.metrics,
        )

    /**
     * Creates a fan-out pipeline where the same extracted data is dispatched to multiple branches in parallel.
     * Each branch in [block] independently transforms (optional) and loads the data.
     */
    fun fanOut(
        bufferSize: Int = etlConfig.bufferSize,
        block: FanOutBuilder.() -> Unit,
    ): EtlPipelineImpl<UntypedRow, UntypedRow> {
        val builder = FanOutBuilder(name = name, etlConfig = etlConfig)
        builder.block()
        require(builder.branches.isNotEmpty()) {
            "fanOut block must define at least one branch with loadWith(...)"
        }
        return EtlPipelineImpl(
            name = name,
            extractor = extractor,
            loaders = builder.branches.toList(),
            bufferSize = bufferSize,
            metrics = etlConfig.metrics,
        )
    }
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
     * Attach a loader to the end of the transformation chain
     */
    fun loadWith(
        loader: DataLoader<UntypedRow>,
        bufferSize: Int = etlConfig.bufferSize,
    ): EtlPipelineImpl<UntypedRow, UntypedRow> =
        EtlPipelineImpl(
            name = name,
            extractor = extractor,
            loaders = listOf(transformer to loader),
            bufferSize = bufferSize,
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
 * DSL receiver for the [ExtractStep.fanOut] block.
 * Accumulates multiple independent (transformer, loader) branches that will share the same extracted data.
 */
class FanOutBuilder internal constructor(
    val name: String,
    internal val etlConfig: EtlConfig,
    internal val branches: MutableList<Pair<DataTransformer<UntypedRow, UntypedRow>, DataLoader<UntypedRow>>> = mutableListOf(),
) {
    fun filter(
        predicate: (UntypedRow) -> Boolean,
    ): FanOutTransformStep = FanOutTransformStep(
        name = name,
        etlConfig = etlConfig,
        transformer = UntypedFilterTransformer(
            name = name,
            metrics = etlConfig.metrics,
            predicate = predicate,
        ),
        builder = this,
    )
    /**
     * Starts a new branch with aggregation by [groupKeys].
     */
    fun aggregateBy(
        vararg groupKeys: String,
        aggregate: (current: UntypedRow, next: UntypedRow) -> UntypedRow,
    ): FanOutTransformStep = FanOutTransformStep(
        name = name,
        etlConfig = etlConfig,
        transformer = UntypedAggregationTransformer(
            name = name,
            bufferSize = etlConfig.transformationBufferSize,
            loggingFrequency = etlConfig.loggingFrequency,
            metrics = etlConfig.metrics,
            groupKeys = groupKeys.toList(),
            aggregate = aggregate,
        ),
        builder = this,
    )


    /**
     * Starts a new branch with the provided [transformer].
     */
    fun transformWith(
        transformer: DataTransformer<UntypedRow, UntypedRow>,
    ): FanOutTransformStep = FanOutTransformStep(
        name = name,
        etlConfig = etlConfig,
        transformer = transformer,
        builder = this,
    )

    /**
     * Adds a pass-through branch (no transformation) that loads data directly via [loader].
     */
    fun loadWith(loader: DataLoader<UntypedRow>) {
        branches.add(NoOpTransformer to loader)
    }
}

/**
 * Represents the transform chain of a single fan-out branch.
 * Call [loadWith] to finalize the branch and register it in the enclosing [FanOutBuilder].
 */
class FanOutTransformStep internal constructor(
    val name: String,
    internal val etlConfig: EtlConfig,
    val transformer: DataTransformer<UntypedRow, UntypedRow>,
    private val builder: FanOutBuilder,
) {
    /**
     * Appends a filter step that excludes rows for which [predicate] returns false.
     */
    fun filter(predicate: (UntypedRow) -> Boolean): FanOutTransformStep = append(
        UntypedFilterTransformer(
            name = name,
            metrics = etlConfig.metrics,
            predicate = predicate,
        )
    )

    /**
     * Appends an aggregation step that groups rows by [groupKeys].
     */
    fun aggregateBy(
        vararg groupKeys: String,
        aggregate: (current: UntypedRow, next: UntypedRow) -> UntypedRow,
    ): FanOutTransformStep = append(
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
     * Appends an additional custom transformation step.
     */
    fun transformWith(transformer: DataTransformer<UntypedRow, UntypedRow>): FanOutTransformStep =
        append(transformer)

    /**
     * Finalizes this branch: registers the (transformer → loader) pair into the enclosing [FanOutBuilder].
     */
    fun loadWith(loader: DataLoader<UntypedRow>) {
        builder.branches.add(transformer to loader)
    }

    private fun append(next: DataTransformer<UntypedRow, UntypedRow>): FanOutTransformStep =
        FanOutTransformStep(
            name = name,
            etlConfig = etlConfig,
            transformer = SequencedTransformer(transformer, next),
            builder = builder,
        )
}

/**
 * Composes two [DataTransformer]s in sequence, so that the output of the first one is fed into the second one.
 */
private class SequencedTransformer<T : EtlRow, M : EtlRow, R : EtlRow>(
    private val first: DataTransformer<T, M>,
    private val second: DataTransformer<M, R>
) : DataTransformer<T, R> {
    override val name: String = "${first.name}+${second.name}"
    override suspend fun transform(groupId: String, collector: Flow<T>): Flow<R> =
        second.transform(groupId, first.transform(groupId, collector))
}

/**
 * No-op transformer used when [ExtractStep.loadWith] is called without any prior transform step.
 */
private object NoOpTransformer : DataTransformer<UntypedRow, UntypedRow> {
    override val name: String = "identity"
    override suspend fun transform(
        groupId: String,
        collector: Flow<UntypedRow>,
    ): Flow<UntypedRow> = collector
}



