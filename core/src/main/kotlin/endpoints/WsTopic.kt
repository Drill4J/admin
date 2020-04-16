package com.epam.drill.admin.endpoints

import com.epam.drill.common.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.locations.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.concurrent.*
import kotlin.collections.set
import kotlin.reflect.*
import kotlin.reflect.full.*

private val regexPathParam = "\\{(.*)}".toRegex()

class WsTopic(override val kodein: Kodein) : KodeinAware {
    val app by instance<Application>()
    val pathToCallBackMapping: MutableMap<URLTopic, Pair<KClass<*>, CallbackWrapper<Any, Any>>> = ConcurrentHashMap()

    suspend operator fun invoke(block: suspend WsTopic.() -> Unit) {
        block(this)
    }

    suspend fun resolve(destination: String): Any {
        if (pathToCallBackMapping.isEmpty()) return ""
        val (callback, param) = getParams(destination)
        val result = callback.resolve(param)
        return result ?: ""
    }

    fun getParams(destination: String): Pair<CallbackWrapper<Any, Any>, Any> {
        val (suitableRout, parameters) = pathToCallBackMapping.suitableRoutWithParameters(destination)

        val (routeClass, callback) = suitableRout.value
        return callback to app.feature(Locations).resolve(routeClass, parameters)
    }
}


typealias Routs = Map<URLTopic, Pair<KClass<*>, CallbackWrapper<Any, Any>>>

fun Routs.suitableRoutWithParameters(destination: String) = run {
    val urlTokens = destination.split("/")
    val filteredRouts = this.filterRoutsByLength(urlTokens).filterRoutsByUrl(urlTokens)
    if (filteredRouts.isEmpty()) throw RuntimeException("A destination '$destination' is not registered")
    val suitableRout = filteredRouts.routWithMinParametersCount()
    val mutableMapOfParam = suitableRout.key.getMapOfParameters(urlTokens)
    val pairsOfParam = mutableMapOfParam.map { (paramName, paramValue) -> paramName to listOf(paramValue) }
    suitableRout to parametersOf(* pairsOfParam.toTypedArray())
}

fun Routs.routWithMinParametersCount() = this.entries.first { it.key == this.keys.min() }

fun Routs.filterRoutsByUrl(urlTokens: List<String>) = this.filter { it.key.isMatched(urlTokens) }

fun Routs.filterRoutsByLength(urlTokens: List<String>) = this.filter { it.key.tokens().size == urlTokens.size }

data class URLTopic(val rawUrl: String) : Comparable<URLTopic> {

    fun tokens() = rawUrl.split("/")

    override fun compareTo(other: URLTopic) = this.rawUrl.count { it == '{' } - other.rawUrl.count { it == '{' }

    fun getMapOfParameters(urlTokens: List<String>) = tokens()
        .mapIndexed { index, pathParam -> index to pathParam }
        .filter { (index, pathParam) -> pathParam != urlTokens[index] && regexPathParam.matches(pathParam) }
        .map { (index, pathParam) ->
            val (pathParamTemplate) = regexPathParam.find(pathParam)!!.destructured
            pathParamTemplate to urlTokens[index]
        }

    fun isMatched(urlTokens: List<String>) =
        this.tokens().filterIndexed { index, token -> token != urlTokens[index] && !token.startsWith("{") }.isEmpty()
}


inline fun <reified R : Any> WsTopic.topic(noinline block: suspend (R) -> Any?) {
    val findAnnotation = R::class.findAnnotation<Location>()
    val path = findAnnotation?.path!!
    @Suppress("UNCHECKED_CAST")
    pathToCallBackMapping[URLTopic(path)] = R::class to CallbackWrapper(block) as CallbackWrapper<Any, Any>
}

class CallbackWrapper<T, R>(val block: suspend (R) -> T?) {
    suspend fun resolve(param: R): T? {
        return block(param)
    }
}

fun serialize(value: Any?): String {
    if (value == null) return ""
    if (value is String) return value
    val serializer = when (value) {
        is List<*> -> ListSerializer(elementSerializer(value))
        is Set<*> -> SetSerializer(elementSerializer(value))
        is Map<*, *> -> MapSerializer(
            elementSerializer(value.keys),
            elementSerializer(value.values)
        )
        is Array<*> -> {
            @Suppress("UNCHECKED_CAST")
            (ArraySerializer(
                elementSerializer(value.asList()) as KSerializer<Any>
            ))
        }
        else -> value::class.serializer()
    }
    @Suppress("UNCHECKED_CAST")
    return serializer as KSerializer<Any> stringify value
}

fun elementSerializer(collection: Collection<*>) = (collection.firstOrNull() ?: "")::class.serializer()
