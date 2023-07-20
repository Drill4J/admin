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
package com.epam.drill.plugins.test2code.global_filter

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.BetweenOp
import com.epam.drill.plugins.test2code.api.routes.*
import com.epam.drill.plugins.test2code.api.routes.Routes.Build.*
import com.epam.drill.plugins.test2code.api.routes.Routes.Build.Filters.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.*
import com.epam.dsm.find.*
import kotlinx.serialization.Serializable

/**
 * @see FilterPayload
 */
@Serializable
data class StoredFilter(
    @Id val id: String,
    val agentId: String,
    val name: String,
    val attributes: List<TestOverviewFilter>,
    val attributesOp: BetweenOp = BetweenOp.AND,
)

fun FilterPayload.toStoredFilter(
    agentId: String,
) = StoredFilter(
    id = id.ifEmpty { genUuid() },
    agentId = agentId,
    name = name,
    attributes = attributes,
    attributesOp = attributesOp,
)

@Serializable
data class FilterDto(
    val name: String,
    val id: String,
)

suspend fun Plugin.sendFilterUpdates(filterId: String, filter: StoredFilter? = null) {
    val filtersRoute = Routes.Build().let(Routes.Build::Filters)
    send(
        buildVersion,
        destination = Filter(id = filterId, filters = filtersRoute),
        message = filter ?: ""
    )
    sendFilters(agentFilters(), filtersRoute)
}

/**
 * Collect and send filters to the UI
 * @features Agent registration
 */
suspend fun Plugin.sendFilters() {
    val filtersRoute = Routes.Build().let(Routes.Build::Filters)
    val storedFilters = agentFilters()
    storedFilters.forEach {
        send(
            buildVersion,
            destination = Filter(id = it.id, filters = filtersRoute),
            message = it
        )
    }
    sendFilters(storedFilters, filtersRoute)
}

private suspend fun Plugin.agentFilters() = storeClient.findBy<StoredFilter> {
    StoredFilter::agentId eq agentId
}.get()

private suspend fun Plugin.sendFilters(
    storedFilters: List<StoredFilter>,
    filtersRoute: Filters,
) {
    val filters = storedFilters.map {
        FilterDto(
            name = it.name,
            id = it.id
        )
    }
    logger.debug { "sending filters $filters..." }
    send(
        buildVersion,
        destination = filtersRoute,
        message = filters
    )
}
