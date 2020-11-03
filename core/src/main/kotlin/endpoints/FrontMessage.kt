package com.epam.drill.admin.endpoints

import com.epam.drill.admin.api.websocket.*
import kotlin.reflect.*
import kotlin.reflect.full.*

typealias FrontMessage = Any

internal fun FrontMessage.postProcess(
    subscription: Subscription?
): Any = subscription?.let { _ ->
    @Suppress("UNCHECKED_CAST")
    val iterable = this as? Iterable<Any>
    iterable?.takeIf(Iterable<*>::any)?.run {
        //TODO remove searchStatement, sortStatement handling
        val (filters: Set<FieldFilter>, sorts: Set<FieldOrder>) = (subscription as? AgentSubscription)?.run {
            val filter = searchStatement?.run {
                setOf(FieldFilter(fieldName, value, FieldOp.CONTAINS))
            }.orEmpty()
            val sort = sortStatement?.run {
                setOf(FieldOrder(fieldName, if (order == "ASC") OrderKind.ASC else OrderKind.DESC))
            }.orEmpty()
            filter + subscription.filters to sort + subscription.orderBy
        } ?: subscription.filters to subscription.orderBy
        takeIf { filters.any() || sorts.any() }?.run {
            val fields = sequenceOf(
                filters.asSequence().map(FieldFilter::field),
                sorts.asSequence().map(FieldOrder::field)
            ).flatten().toSet()
            val clazz = first()::class
            val propMap = clazz.memberProperties.asSequence()
                .filter { it.name in fields }
                .associateBy { it.name }
            val predicate: ((Any) -> Boolean)? = filters.asSequence()
                .filter { it.field in propMap }
                .takeIf { it.any() }
                ?.toPredicate(propMap)
            val comparator = sorts.asSequence()
                .filter { it.field in propMap }
                .takeIf { it.any() }
                ?.toComparator(propMap)
            (predicate?.let { filter(it) } ?: this).run {
                comparator?.let { sortedWith(it) } ?: this
            }
        }
    }
} ?: this

private fun Sequence<FieldFilter>.toPredicate(
    propMap: Map<String, KProperty1<out Any, Any?>>
): (Any) -> Boolean = { msg: Any ->
    fold(true) { acc, f ->
        acc && propMap.getValue(f.field).call(msg)?.toString().orEmpty().let {
            when (f.op) {
                FieldOp.EQ -> it == f.value
                FieldOp.CONTAINS -> f.value in it
            }
        }
    }
}

private fun Sequence<FieldOrder>.toComparator(
    propMap: Map<String, KProperty1<out Any, Any?>>
) = Comparator<Any> { msg1, msg2 ->
    fold(0) { acc, sort ->
        if (acc == 0) {
            val prop = propMap.getValue(sort.field)
            val val1 = prop.call(msg1)
            val val2 = prop.call(msg2)
            val selector = { any: Any? -> any as? Comparable<*> }
            when (sort.order) {
                OrderKind.ASC -> compareValuesBy(val1, val2, selector)
                OrderKind.DESC -> compareValuesBy(val2, val1, selector)
            }
        } else acc
    }
}
