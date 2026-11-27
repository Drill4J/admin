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
package com.epam.drill.admin.metrics.route

import com.epam.drill.admin.common.config.ApiResponse
import com.epam.drill.admin.metrics.service.MetricsService
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

@Resource("/groups")
class Groups {
    @Resource("/{groupId}")
    class GroupId(
        val parent: Groups,
        val groupId: String
    ) {

    }
}

fun Route.groupRoutes() {
    getGroups()
}

fun Route.getGroups() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Groups> {
        println("!!! getGroups")
        val data = metricsService.getGroups()
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}