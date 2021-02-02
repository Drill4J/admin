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
package com.epam.drill.admin.storage


class ObservableSetStorage<T>(private val targetSet: MutableSet<T> = mutableSetOf()) : MutableSet<T> by targetSet {
    val onUpdate: MutableSet<(MutableSet<T>) -> Unit> = mutableSetOf()
    val onAdd: MutableSet<(T) -> Unit> = mutableSetOf()
    val onRemove: MutableSet<(T) -> Unit> = mutableSetOf()
    val onClear: MutableSet<(MutableSet<T>) -> Unit> = mutableSetOf()


    override fun add(element: T): Boolean {
        val add = targetSet.add(element)
        onAdd.forEach { it(element) }
        onUpdate.forEach { it(targetSet) }
        return add
    }

    override fun remove(element: T): Boolean {
        val remove = targetSet.remove(element)
        onRemove.forEach { it(element) }
        onUpdate.forEach { it(targetSet) }
        return remove
    }

  override fun clear() {
        targetSet.clear()
        onClear.forEach { it(targetSet) }
        onUpdate.forEach { it(targetSet) }
    }
}

fun test() {
    val kta = ObservableSetStorage(mutableSetOf<String>())
    kta.onAdd += { item ->
        println("$item added")
    }
    kta.onUpdate += { set ->
        println("$set updated")
    }
    kta.onRemove += { item ->
        println("$item removed")
    }
    kta.onClear += {
        println("cleaned")
    }
    kta.add("2313")
    kta.add("1231")
    kta.remove("1231")
    kta.add("1")
    kta.clear()
}
