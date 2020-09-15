package com.epam.drill.admin.endpoints.plugin

import com.epam.drill.admin.plugin.*
import kotlinx.serialization.*
import kotlin.reflect.*
import kotlin.reflect.full.*


@Serializable
data class SearchStatement(val fieldName: String, val value: String)

@Serializable
data class SortStatement(val fieldName: String, val order: String)

internal fun FrontMessage.processWithSubscription(subscription: Subscription?): Any =
    when (subscription) {
        is AgentSubscription ->
            applyFilter(subscription.searchStatement)
                .applySort(subscription.sortStatement)
        else -> this
    }


private fun FrontMessage.applyFilter(statement: SearchStatement?): FrontMessage = statement?.let {
    runCatching {
        @Suppress("UNCHECKED_CAST")
        (this as? Iterable<Any>)?.filter { fMessage ->
            val propertyValue = fMessage::class.getProperty(statement.fieldName).call(fMessage)
            "$propertyValue".contains(statement.value, ignoreCase = true)
        }
    }.getOrNull()
} ?: this

@Suppress("UNCHECKED_CAST")
internal fun FrontMessage.applySort(statement: SortStatement?): Any = statement?.let {
    runCatching {
        val selector: (Any) -> Comparable<Any> = {
            it::class.getProperty(statement.fieldName).call(it) as Comparable<Any>
        }
        val data = this as Iterable<Any>
        if (statement.order == "ASC") data.sortedBy(selector) else data.sortedByDescending(selector)
    }.getOrNull()
} ?: this

private fun KClass<out Any>.getProperty(name: String) =
    memberProperties.first { it.name == name }
