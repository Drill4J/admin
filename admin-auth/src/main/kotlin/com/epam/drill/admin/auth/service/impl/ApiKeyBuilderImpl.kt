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
package com.epam.drill.admin.auth.service.impl

import com.epam.drill.admin.auth.service.ApiKey
import com.epam.drill.admin.auth.service.ApiKeyBuilder

class ApiKeyBuilderImpl: ApiKeyBuilder {
    override fun format(apiKey: ApiKey): String {
        return "${apiKey.identifier}_${apiKey.secret}"
    }

    override fun parse(apiKey: String): ApiKey {
        val parts = apiKey.split("_")
        if (parts.size != 2) throw IllegalArgumentException("Invalid api key format: must be 2 parts separated by '_'")
        if (parts.first().isEmpty()) throw IllegalArgumentException("Invalid api key format: first part must not be empty")
        if (parts.last().isEmpty()) throw IllegalArgumentException("Invalid api key format: second part must not be empty")
        return parts.let { ApiKey(it.first().toInt(), it.last()) }
    }
}