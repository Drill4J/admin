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
import com.epam.drill.plugins.test2code.api.routes.*
import com.epam.drill.plugins.test2code.api.routes.Routes.Build.Attributes.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.dsm.*
import com.epam.dsm.find.*
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.full.*

@Serializable
data class LabelMarker(val name: String, val isLabel: Boolean = true)

private val propertyNames = staticPropertyNames()

/**
 * Collect and send labels to the UI
 * @features Agent registration, Scope finishing
 */
suspend fun Plugin.sendLabels() {
    val attributesRoute = Routes.Build().let(Routes.Build::Attributes)

    val sessionIds = storeClient.sessionIds(agentKey)
    //TODO can be replace on sql with unique labels condition, instead toSet()
    val allLabels = storeClient.getAll<Label>().toSet()
    val staticLabels = storeClient.staticLabels(sessionIds)

    val labels = allLabels.fold(staticLabels) { acc, tag ->
        acc[LabelMarker(tag.name)]?.add(tag.value.lowercase(Locale.getDefault()))
            ?: acc.put(LabelMarker(tag.name), mutableSetOf(tag.value))
        acc
    }

    send(
        buildVersion,
        destination = attributesRoute,
        message = labels.keys
    )

    labels.forEach { (label, values) ->
        logger.trace { "send tags for '$label' with values '$values'" }
        send(
            buildVersion,
            destination = AttributeValues(attributesRoute, label.name),
            message = values
        )
    }
}


private suspend fun StoreClient.staticLabels(
    sessionIds: List<String>,
): MutableMap<LabelMarker, MutableSet<String>> = propertyNames.fold(mutableMapOf()) { acc, tag ->
    val values = if (tag.contains(PATH_DELIMITER)) {
        attrValues(sessionIds, tag.substringAfter(PATH_DELIMITER), isTestDetails = true)
    } else attrValues(sessionIds, tag)
    acc[LabelMarker(tag, false)]?.addAll(values) ?: acc.put(LabelMarker(tag, false), values.toMutableSet())
    acc
}

suspend inline fun StoreClient.attrValues(
    sessionIds: List<String>,
    attribute: String,
    isTestDetails: Boolean = false,
): List<String> = findBy<TestOverview> { containsParentId(sessionIds) }
    .distinct()
    .getStrings(FieldPath(TestOverview::details.name, attribute).takeIf { isTestDetails }
        ?: FieldPath(attribute))

fun staticPropertyNames(): Set<String> {
    val details = TestOverview::details.name
    val testOverviewAttributes = TestOverview::class.nameFields(
        exceptFields = listOf(details, TestOverview::testId.name)
    )
    val testDetailsAttr = TestDetails::class.nameFields(
        exceptFields = listOf(TestDetails::metadata.name, TestDetails::params.name, TestDetails::labels.name),
        prefix = "$details$PATH_DELIMITER")
    return testOverviewAttributes.union(testDetailsAttr)
}

private fun KClass<*>.nameFields(
    exceptFields: List<String> = emptyList(),
    prefix: String = "",
): List<String> {
    return this.memberProperties.filterNot { exceptFields.contains(it.name) }.map {
        "$prefix${it.name}"
    }
}

