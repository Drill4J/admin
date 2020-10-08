package com.epam.drill.admin.store

import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

private val emptyByteArray = byteArrayOf()

@Serializable
internal class StoredMessage(
    val kind: MessageKind,
    val className: String = "",
    val data: ByteArray = emptyByteArray
)

internal enum class MessageKind {
    OBJECT,
    LIST,
    SET
}

@Serializable
internal class DataParts(
    val list: List<ByteArray> = emptyList()
)

private val emptyStoredList = StoredMessage(
    kind = MessageKind.LIST
)

private val emptyStoredSet = StoredMessage(
    kind = MessageKind.SET
)

internal fun Any.toStoredMessage(): StoredMessage = when (this) {
    is StoredMessage -> error("Conversion StoredMessage -> StoredMessage is forbidden!")
    is Collection<*> -> {
        @Suppress("UNCHECKED_CAST")
        val collection = this as Collection<Any>
        val kind = if (this is Set<*>) MessageKind.SET else MessageKind.LIST
        if (collection.any()) {
            val cls = collection.first()::class
            val data = if (collection.any()) {
                when (cls) {
                    String::class -> collection.map { (it as String).toByteArray() }
                    else -> cls.anySerializer().let { sr ->
                        collection.map { ProtoBuf.dump(sr, it) }
                    }
                }.let { ProtoBuf.dump(DataParts.serializer(), DataParts(it)) }
            } else emptyByteArray
            StoredMessage(
                kind = kind,
                className = cls.jvmName,
                data = data
            )
        } else when (kind) {
            MessageKind.SET -> emptyStoredSet
            else -> emptyStoredList
        }
    }
    else -> StoredMessage(
        MessageKind.OBJECT,
        this::class.jvmName,
        ProtoBuf.dump(this::class.anySerializer(), this)
    )
}

internal fun StoredMessage.toMessage(
    classLoader: ClassLoader = Thread.currentThread().contextClassLoader
): Any = when {
    kind == MessageKind.OBJECT -> {
        ProtoBuf.load(className.classNameSerializer(classLoader), data)
    }
    data.any() -> {
        val parts = ProtoBuf.load(DataParts.serializer(), data)
        val list = when (className) {
            String::class.jvmName -> parts.list.map { String(it) }
            else -> parts.list.map { ProtoBuf.load(className.classNameSerializer(classLoader), it) }
        }
        list.takeIf { kind == MessageKind.LIST } ?: list.toSet()
    }
    else -> when (kind) {
        MessageKind.SET -> emptySet<Any>()
        else -> emptyList<Any>()
    }
}

@Suppress("UNCHECKED_CAST")
private fun KClass<*>.anySerializer() = serializer() as KSerializer<Any>

private fun String.classNameSerializer(classLoader: ClassLoader) = run {
    classLoader.loadClass(this).kotlin
}.serializer()
