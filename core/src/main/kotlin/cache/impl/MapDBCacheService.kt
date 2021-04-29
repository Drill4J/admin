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
package com.epam.drill.admin.cache.impl

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.protobuf.*
import mu.*
import org.mapdb.*
import org.mapdb.Serializer
import java.util.concurrent.*

private val logger = KotlinLogging.logger {}

class MapDBCacheService : CacheService {

    private val dbMemory = DBMaker.memoryDirectDB().make()
    //TODO EPMDJ-7018
    val serializers = HashMap<Any, KSerializer<Any>>()

    @Suppress("UNCHECKED_CAST")
    override fun <K, V> getOrCreate(id: Any, qualifier: Any, replace: Boolean): Cache<K, V> {
        val cacheId = cacheId(id, qualifier)
        dbMemory.get<Any?>(cacheId)?.let {
            logger.trace { "get cache $id$qualifier replace $replace" }
            return MapDBCache(qualifier, it as HTreeMap<K, ByteArray>, serializers)
        }
        val map: HTreeMap<out Any?, out Any?> = dbMemory.hashMap(cacheId)
            .valueSerializer(Serializer.BYTE_ARRAY)
            .expireStoreSize(512 * 1024 * 1024)//with 524Mb space limit
            .expireAfterGet(60, TimeUnit.MINUTES)
            .expireAfterCreate()
            .expireAfterGet()
            .create()
        logger.debug { "create cache with $id$qualifier replace $replace" }
        return MapDBCache(qualifier, map as HTreeMap<K, ByteArray>, serializers)
    }

    fun stats(): List<Pair<String, String>> = dbMemory.getAll().map {
        val cacheBytesSize = hTreeMap(it)
            .mapNotNull { entry -> entry.value }
            .sumBy { bytes -> bytes.size }
        val kb = 1024.0
        val stats = "size: ${cacheBytesSize / kb} KB (${cacheBytesSize / (kb * 1024)} MB)"
        it.key to stats
    }

    fun clear() = dbMemory.getAll().map {
        hTreeMap(it).clear()
    }

    fun clear(id: Any, qualifier: Any) = dbMemory.get<HTreeMap<Any, ByteArray>>(cacheId(id, qualifier))?.clear()

    private fun cacheId(id: Any, qualifier: Any) = "$id$qualifier"

    @Suppress("UNCHECKED_CAST")
    private fun hTreeMap(it: Map.Entry<String, Any?>): HTreeMap<Any, ByteArray> = it.value as HTreeMap<Any, ByteArray>

}

internal class MapDBCache<K, V>(
    override val qualifier: Any,
    private val map: HTreeMap<K, ByteArray>,
    private val serializers: HashMap<Any, KSerializer<Any>>,
) : Cache<K, V> {

    override fun get(key: K): V? = map[key]?.let {
        deserializeValue(key, it).also { logger.trace { "get: key $key" } }
    }

    override fun set(key: K, value: V): V? {
        if (value != null) {
            val serializer = getKSerializer(value)
            logger.trace { "set: key $key" }
            map[key] = ProtoBuf.dump(serializer, value)
            if (value != "") { //TODO EPMDJ-6817
                serializer.store(key)
            }
            return get(key)
        }
        logger.trace { "skip setting for '$key' because value is null or empty" }
        return null
    }

    override fun remove(key: K): V? {
        val removed = map.remove(key)
        return if (removed != null)
            deserializeValue(key, removed).also {
                logger.trace { "deleted: key $key" }
            }
        else null.also {
            logger.trace { "it didn't delete for key '$key'" }
        }
    }

    @Suppress("UNCHECKED_CAST", "TYPE_INFERENCE_ONLY_INPUT_TYPES_WARNING")
    private fun <K> K.store(key: K) {
        serializers[key!!] = serializers[key]?.takeIf {
            it.descriptor != ListSerializer(""::class.serializer()).descriptor
        } ?: this as KSerializer<Any>
        logger.trace { "set ser $key $this" }
    }

    @Suppress("UNCHECKED_CAST", "TYPE_INFERENCE_ONLY_INPUT_TYPES_WARNING")
    private fun deserializeValue(key: K, it: ByteArray): V {
        if (it.contentEquals(ProtoBuf.dump(""))) { //TODO EPMDJ-6817
            return ProtoBuf.load(String.serializer() as KSerializer<V>, it)
        }
        return ProtoBuf.load(serializers[key] as KSerializer<V>, it)
    }
}

