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
package com.epam.drill.admin.config

import com.epam.drill.admin.api.routes.ApiRoot
import com.epam.drill.admin.auth.config.AuthConfig
import com.epam.drill.admin.auth.model.toView
import de.nielsfalk.ktor.swagger.examples
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

fun Route.uiConfigRoute() {
    val authConfig by closestDI().instance<AuthConfig>()

    get<ApiRoot.UIConfig>(
        "Return UI configuration"
            .examples()
            .responds(
                ok<UIConfigDto>()
            )
    ) {
        call.respond(
            HttpStatusCode.OK, UIConfigDto(
                auth = authConfig.toView()
            )
        )
    }
}