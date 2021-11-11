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

//import com.epam.kodux.*
//import jetbrains.exodus.entitystore.*
//import java.io.*
//todo remove it
//class CommonStore(private val baseDir: File) : Closeable {
//
//    val client: StoreClient get() = _client.value
//
//    private val _client: Lazy<StoreClient> = lazy {
//        val store = PersistentEntityStores.newInstance(baseDir.resolve("common"))
//        StoreClient(store)
//    }
//
//
//    override fun close() {
//        if (_client.isInitialized()) {
//            client.closeStore()
//        }
//    }
//}

val commonStoreDsm = com.epam.dsm.StoreClient("common")
