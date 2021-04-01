package com.epam.drill.admin.cache.impl

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*
import mu.*
import org.mapdb.*
import org.mapdb.Serializer
import java.util.concurrent.*

private val logger = KotlinLogging.logger {}

class MapDBCacheService : CacheService {

    private val dbMemory = DBMaker.memoryDirectDB().make()

    @Suppress("UNCHECKED_CAST")
    override fun <K, V> getOrCreate(id: Any, qualifier: Any, replace: Boolean): Cache<K, V> {
        val cacheId = "$id$qualifier"
        dbMemory.get<Any?>(cacheId)?.let {
            logger.trace { "getOrCreate cache $id$qualifier replace $replace" }
            return MapDBCache(it as HTreeMap<K, ByteArray>, qualifier)
        }
        val map: HTreeMap<out Any?, out Any?> = dbMemory.hashMap(cacheId)
            .valueSerializer(Serializer.BYTE_ARRAY)
            //todo configs:
            .expireAfterGet(60, TimeUnit.MINUTES)
//            .expireStoreSize(10 * 1024 * 1024 * 1024)//with 1GB space limit
//            .expireMaxSize(128)//elements?
            .create()
        logger.debug { "create cache with $id$qualifier replace $replace" }
        return MapDBCache(map as HTreeMap<K, ByteArray>, qualifier)
    }
}

val serStore = HashMap<Any, KSerializer<Any>>()

@Suppress("UNCHECKED_CAST", "TYPE_INFERENCE_ONLY_INPUT_TYPES_WARNING")
private fun <T, K> KSerializer<T>.store(key: K) {
    if (serStore[key] == null)
        serStore[key!!] = this as KSerializer<Any>
}

internal class MapDBCache<K, V>(
    val map: HTreeMap<K, ByteArray>,
    override val qualifier: Any,
) : Cache<K, V> {

    override fun get(key: K): V? = map[key]?.let {
        deserializeValue(key, it).also { logger.trace { "get: key $key value $it" } }
    }

    override fun set(key: K, value: V): V? {
        if (value != null && value != "") {
            val serializer = getKSerializer(value)
            logger.trace { "set: key $key value '$value'" }
            map[key] = ProtoBuf.dump(serializer, value)
            serializer.store(key)
            return get(key)
        }
        logger.trace { "skip setting for '$key' because value is null or empty" }
        return null
    }

    override fun remove(key: K): V? {
        val removed = map.remove(key)
        return if (removed != null)
            deserializeValue(key, removed).also {
                logger.trace { "deleted: key $key value $it" }
            }
        else null.also {
            logger.trace { "it didn't delete for key '$key'" }
        }
    }

    @Suppress("UNCHECKED_CAST", "TYPE_INFERENCE_ONLY_INPUT_TYPES_WARNING")
    private fun deserializeValue(key: K, it: ByteArray) = ProtoBuf.load(serStore[key] as KSerializer<V>, it)

}

