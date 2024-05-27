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
package com.epam.drill.admin.auth.route

import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.locations.get
import io.ktor.server.routing.*

@Location("/try-api-key")
object TryApiKey

fun Route.tryApiKeyRoute() {
    get<TryApiKey> {
        call.ok("API key is valid")
    }
}
