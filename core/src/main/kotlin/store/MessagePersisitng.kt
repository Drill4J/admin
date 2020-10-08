package com.epam.drill.admin.store

import com.epam.drill.admin.util.*
import com.epam.kodux.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

@Serializable
internal class Stored(
    @Id val id: String,
    val data: ByteArray
)

internal suspend fun StoreClient.storeMessage(
    id: String,
    message: Any
) {
    val storedMessage = message.toStoredMessage()
    val bytes = ProtoBuf.dump(StoredMessage.serializer(), storedMessage)
    val compressed = Zstd.compress(bytes)
    store(Stored(id, compressed))
}

internal suspend fun StoreClient.readMessage(
    id: String,
    classLoader: ClassLoader = Thread.currentThread().contextClassLoader
): Any? = findById<Stored>(id)?.let { stored ->
    val bytes = Zstd.decompress(stored.data)
    val storedMessage = ProtoBuf.load(StoredMessage.serializer(), bytes)
    storedMessage.toMessage(classLoader)
}

internal suspend fun StoreClient.deleteMessage(id: String) = deleteById<Stored>(id)
