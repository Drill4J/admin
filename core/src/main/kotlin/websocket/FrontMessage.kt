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
package com.epam.drill.admin.websocket

import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.api.websocket.Pagination.*
import com.epam.drill.admin.common.serialization.*
import kotlinx.serialization.json.*
import java.lang.reflect.*
import kotlin.reflect.*
import kotlin.reflect.full.*

typealias FrontMessage = Any

internal fun FrontMessage.postProcessFilter(
    subscription: Subscription?,
): Any = subscription?.let {
    @Suppress("UNCHECKED_CAST")
    val iterable = this as? Iterable<Any>
    iterable?.run {
        val processed = takeIf(Iterable<*>::any)?.run {
            val filters: Set<FieldFilter> = subscription.filters
            val sorts: Set<FieldOrder> = subscription.orderBy
            takeIf { filters.any() || sorts.any() }?.run {
                val fields = sequenceOf(
                    filters.asSequence().map(FieldFilter::field),
                    sorts.asSequence().map(FieldOrder::field)
                ).flatten().toSet()
                val predicate: ((Any) -> Boolean)? = filters.asSequence()
                    .filter { it.field in fields }
                    .takeIf { it.any() }
                    ?.toPredicate()
                val comparator = sorts.asSequence()
                    .filter { it.field in fields }
                    .takeIf { it.any() }
                    ?.toComparator()
                (predicate?.let { filter(it) } ?: this).run {
                    comparator?.let { sortedWith(it) } ?: this
                }
            }
        } ?: this
        processed.toOutput(
            subscription = subscription,
            totalCount = iterable.count()
        )
    }
} ?: this

private fun Sequence<FieldFilter>.toPredicate(): (Any) -> Boolean = { filteringMsg: Any ->
    fold(true) { acc, filter ->
        acc && findValue(filter.field, filteringMsg)?.toString().orEmpty().let {
            when (filter.op) {
                FieldOp.EQ -> it == filter.value
                FieldOp.CONTAINS -> it.contains(filter.value, ignoreCase = true)
            }
        }
    }
}

private fun Sequence<FieldOrder>.toComparator(): Comparator<Any> = Comparator { msg1, msg2 ->
    fold(0) { acc, sort ->
        if (acc == 0) {
            val val1 = findValue(sort.field, msg1)
            val val2 = findValue(sort.field, msg2)
            val selector = { any: Any? -> any as? Comparable<*> }
            when (sort.order) {
                OrderKind.ASC -> compareValuesBy(val1, val2, selector)
                OrderKind.DESC -> compareValuesBy(val2, val1, selector)
            }
        } else acc
    }
}

const val delimiter = "."

/**
 * @param nameField - can be "field" or nested with delimiter "field.nestedFiled"
 * @param instance first of field
 * @return value of the field
 */
private fun findValue(
    nameField: String,
    instance: Any,
): Any? {
    val firstFiled = nameField.substringBefore(delimiter)
    val fieldValue = instance.javaClass.kotlin.memberProperties.first {
        it.name == firstFiled
    }.get(instance)
    if (nameField.contains(delimiter)) {
        return findValue(nameField.substringAfter(delimiter), fieldValue ?: throw RuntimeException())
    }
    return fieldValue
}

private fun Iterable<Any>.toOutput(
    subscription: Subscription,
    totalCount: Int,
): Any = when (subscription.output) {
    OutputType.LIST -> {
        val items = (this as? List<Any> ?: toList()).toJsonList()
        val result = paginateIfNeed(subscription, items)
        ListOutput(
            totalCount = totalCount,
            filteredCount = result.count(),
            items = result
        )
    }
    else -> this
}

private fun Iterable<Any>.paginateIfNeed(
    subscription: Subscription,
    items: List<JsonElement>,
): List<JsonElement> {
    val pagination = subscription.pagination
    return if (pagination != Pagination.empty) {
        val fromIndex = pagination.pageIndex * pagination.pageSize
        items.subList(fromIndex, minOf(fromIndex + pagination.pageSize, count()))
    } else items
}
