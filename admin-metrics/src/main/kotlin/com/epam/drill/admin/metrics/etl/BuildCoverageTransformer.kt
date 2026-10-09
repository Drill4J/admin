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

import com.epam.drill.admin.etl.DataTransformer
import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.UntypedRow
import com.epam.drill.admin.etl.config.EtlMeter
import com.epam.drill.admin.etl.flow.LruMap
import com.epam.drill.admin.etl.impl.UntypedPreparedSql
import com.epam.drill.admin.metrics.config.executeQueryReturnMap
import com.epam.drill.admin.metrics.config.fromResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.postgresql.util.PGobject
import java.time.Instant

class BuildCoverageTransformer(
    override val name: String,
    private val database: Database,
    private val buildProbeLayoutCacheSize: Int,
    private val metrics: EtlMeter,
) : DataTransformer<UntypedRow, UntypedRow> {

    private val logger = KotlinLogging.logger {}

    private data class MethodProbeInfo(val methodPos: Int, val probeStartPos: Int, val probesCount: Int)
    private data class BuildProbeLayout(val methods: Map<String, MethodProbeInfo>, val totalProbes: Int) {
        val totalMethods = methods.size
    }

    override suspend fun transform(
        context: EtlContext,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        collector: Flow<UntypedRow>,
        onTransformationProgress: suspend (Instant) -> Unit,
    ): Flow<UntypedRow> = flow {
        val buildProbeLayout = LruMap<Triple<String, String, String>, BuildProbeLayout>(maxSize = buildProbeLayoutCacheSize)

        collector.collect { row ->
            val groupId = row["group_id"] as? String ?: error("Missing group_id in row: $row")
            val appId = row["app_id"] as? String ?: error("Missing app_id in row: $row")
            val buildId = row["build_id"] as? String ?: error("Missing build_id in row: $row")
            val methodId = row["method_id"] as? String ?: error("Missing method_id in row: $row")
            val probes = row["probes"] as? PGobject ?: error("Missing probes in row: $row")

            val (layout, _) = buildProbeLayout.compute(Triple(groupId, appId, buildId)) { value ->
                value ?: loadBuildProbeLayout(groupId, appId, buildId)
            }

            val info = layout.methods[methodId] ?: run {
                logger.warn { "ETL transformer [$name] skipping method [$methodId] because it is not found in build [$buildId]" }
                onTransformationProgress(row.timestamp)
                return@collect
            }

            val codeProbes = positionProbes(probes, info.probeStartPos, info.probesCount, layout.totalProbes)
            val testedMethod = probes.value?.contains('1') ?: false
            val singleProbe = PGobject().apply {
                type = "varbit"
                value = if (testedMethod) "1" else "0"
            }
            val methodProbes = positionProbes(singleProbe, info.methodPos, 1, layout.totalMethods)

            emit(
                UntypedRow(
                    row.timestamp,
                    (row as Map<String, Any?>) + ("code_probes" to codeProbes) + ("method_probes" to methodProbes)
                )
            )
        }
    }

    private suspend fun loadBuildProbeLayout(groupId: String, appId: String, buildId: String): BuildProbeLayout {
        val methods = mutableMapOf<String, MethodProbeInfo>()
        val preparedSql = UntypedPreparedSql.prepareSql(
            fromResource("/metrics/db/etl/build_coverage_transformer.sql")
        )
        val args = preparedSql.getArgs(
            UntypedRow(
                Instant.EPOCH, mapOf(
                    "group_id" to groupId,
                    "app_id" to appId,
                    "build_id" to buildId
                )
            )
        )
        val rows = newSuspendedTransaction(context = Dispatchers.IO, db = database) {
            connection.autoCommit = false
            connection.readOnly = true
            executeQueryReturnMap(
                preparedSql.getSql(),
                *args.toTypedArray()
            )
        }
        rows.forEachIndexed { methodPos, row ->
            val mid = row["method_id"] as String
            val probeStartPos = (row["probe_start_pos"] as Number).toInt()
            val probesCount = (row["probes_count"] as Number).toInt()
            methods[mid] = MethodProbeInfo(methodPos, probeStartPos, probesCount)
        }
        val totalProbes = methods.values.maxOfOrNull { it.probeStartPos + it.probesCount } ?: 0
        logger.debug { "ETL transformer [$name] loaded probe layout for build $buildId: ${methods.size} methods, $totalProbes total probes" }
        return BuildProbeLayout(methods, totalProbes)
    }

    private fun positionProbes(probes: PGobject, probeStartPos: Int, probesCount: Int, totalProbes: Int): PGobject {
        val str = probes.value ?: ""
        return PGobject().apply {
            type = "varbit"
            value = "0".repeat(probeStartPos) + str + "0".repeat(totalProbes - probeStartPos - probesCount)
        }
    }
}