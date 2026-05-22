package com.epam.drill.admin.etl.impl

import com.epam.drill.admin.etl.DataTransformer
import com.epam.drill.admin.etl.UntypedRow
import com.epam.drill.admin.etl.config.EtlMeter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow

class UntypedFilterTransformer(
    override val name: String,
    private val metrics: EtlMeter,
    private val predicate: (UntypedRow) -> Boolean
) : DataTransformer<UntypedRow, UntypedRow> {
    override suspend fun transform(
        groupId: String,
        collector: Flow<UntypedRow>,
    ): Flow<UntypedRow> {
        val rowsTransformed = metrics.rowsTransformed(name, groupId)
        val rowsEmitted = metrics.rowsEmitted(name, groupId)
        return flow {
            collector.filter {
                predicate(it).also {
                    rowsTransformed.increment()
                }
            }.collect { row ->
                rowsEmitted.increment()
                emit(row)
            }
        }
    }
}