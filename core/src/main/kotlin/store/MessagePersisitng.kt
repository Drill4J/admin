/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.admin.store

import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.util.*
import com.epam.kodux.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

@Serializable
internal class Stored(
    @Id val id: String,
    val data: ByteArray,
)

internal suspend fun StoreClient.storeMessage(
    id: String,
    message: Any,
): Int {
    val storedMessage = message.toStoredMessage()
    val bytes = ProtoBuf.dump(StoredMessage.serializer(), storedMessage)
    val compressed = Zstd.compress(bytes)
    store(Stored(id, compressed))
    return compressed.size
}

internal suspend fun StoreClient.readMessage(
    id: String,
    classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
): Any? = findById<Stored>(id)?.let { stored ->
    val bytes = Zstd.decompress(stored.data)
    val storedMessage = ProtoBuf.load(StoredMessage.serializer(), bytes)
    storedMessage.toMessage(classLoader)
}

internal suspend fun StoreClient.deleteMessage(id: String) = deleteById<Stored>(id)
