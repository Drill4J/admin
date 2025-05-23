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
package com.epam.drill.admin.metrics.views

class PagedList<T>(
    val page: Int,
    val pageSize: Int,
    val items: List<T>,
    val total: Long? = null
)

suspend fun <T> pagedListOf(
    page: Int,
    pageSize: Int,
    getItems: suspend (offset: Int, limit: Int) -> List<T>
): PagedList<T> {
    val items = getItems((page - 1) * pageSize, pageSize)
    return PagedList(
        page, pageSize, items, when {
            items.size < pageSize -> ((page - 1) * pageSize + items.size).toLong()
            else -> null
        }
    )
}

suspend infix fun <T> PagedList<T>.withTotal(getTotal: suspend () -> Long): PagedList<T> {
    return PagedList(page, pageSize, items, this.total ?: getTotal())
}