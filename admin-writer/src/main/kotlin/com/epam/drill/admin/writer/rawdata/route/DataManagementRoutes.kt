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
package com.epam.drill.admin.writer.rawdata.route

import com.epam.drill.admin.common.principal.User
import com.epam.drill.admin.common.route.ok
import com.epam.drill.admin.writer.rawdata.service.DataManagementService
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.resources.delete
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import mu.KotlinLogging
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import kotlin.getValue

private val logger = KotlinLogging.logger {}

@Resource("/groups")
class Groups() {
    @Resource("/{groupId}")
    class Id(val parent: Groups, val groupId: String) {
        @Resource("/apps")
        class Apps(val parent: Groups.Id) {
            @Resource("/{appId}")
            class Id(val parent: Apps, val appId: String) {
                @Resource("/builds")
                class Builds(val parent: Apps.Id) {
                    @Resource("/{buildId}")
                    class Id(val parent: Builds, val buildId: String)
                }
            }
        }
        @Resource("/tests")
        class Tests(val parent: Groups.Id) {
            @Resource("/sessions")
            class Sessions(val parent: Tests) {
                @Resource("/{testSessionId}")
                class Id(val parent: Sessions, val testSessionId: String)
            }
        }
    }
}

fun Route.dataManagementRoutes() {
    route("/data-management") {
        deleteBuildData()
        deleteTestSessionData()
    }
}

fun Route.deleteBuildData() {
    val dataManagementService by closestDI().instance<DataManagementService>()

    delete<Groups.Id.Apps.Id.Builds.Id> { params ->
        dataManagementService.deleteBuildData(
            groupId = params.parent.parent.parent.parent.groupId,
            appId = params.parent.parent.appId,
            buildId = params.buildId,
            user = call.principal<User>()
        )
        call.ok("Build data deleted successfully")
    }
}

fun Route.deleteTestSessionData() {
    val dataManagementService by closestDI().instance<DataManagementService>()

    delete<Groups.Id.Tests.Sessions.Id> { params ->
        dataManagementService.deleteTestSessionData(
            groupId = params.parent.parent.parent.groupId,
            testSessionId = params.testSessionId,
            user = call.principal<User>()
        )
        call.ok("Test session data deleted successfully")
    }
}