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
package com.epam.drill.admin.metrics.config

interface SqlBuilder {
    fun append(sqlFragment: String, vararg params: Any?)
    fun appendOptional(sqlFragment: String, vararg params: Any?)
}

class SqlBuilderImpl(
    val sqlQuery: StringBuilder = StringBuilder(),
    val params: MutableList<Any?> = mutableListOf()
) : SqlBuilder {

    override fun append(sqlFragment: String, vararg params: Any?) {
        sqlQuery.append(sqlFragment)
        this.params.addAll(params)
    }

    override fun appendOptional(sqlFragment: String, vararg params: Any?) {
        if (params.any { it == null || (it is String && it.isBlank()) }) {
            return
        }
        sqlQuery.append(sqlFragment)
        this.params.addAll(params)
    }
}