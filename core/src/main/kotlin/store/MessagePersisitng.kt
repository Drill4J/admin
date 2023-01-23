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
package com.epam.drill.admin.store

import com.epam.drill.admin.common.serialization.dump
import com.epam.drill.admin.common.serialization.load
import com.epam.dsm.Id
import com.epam.dsm.StoreClient
import com.epam.dsm.serializer.BinarySerializer
import com.epam.dsm.serializer.BynariaData
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf

@Serializable
internal class Stored(
    @Id val id: String,
    @Serializable(with = BinarySerializer::class)
    val data: BynariaData,
)

//todo do we need protobuf? in db clients cannot see an object
internal suspend fun StoreClient.storeMessage(
    id: String,
    message: Any,
    agentKey: String
): Int {
    val storedMessage = message.toStoredMessage()
    val bytes = ProtoBuf.dump(StoredMessage.serializer(), storedMessage)
    return store(Stored(id, BynariaData(byteArray = bytes, agentKey = agentKey))).data.byteArray.size
}

internal suspend fun StoreClient.readMessage(
    id: String,
    classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
): Any? = findById<Stored>(id)?.let { stored ->
    val storedMessage = ProtoBuf.load(StoredMessage.serializer(), stored.data.byteArray)
    storedMessage.toMessage(classLoader)
}

internal suspend fun StoreClient.deleteMessage(id: String) = deleteById<Stored>(id)
