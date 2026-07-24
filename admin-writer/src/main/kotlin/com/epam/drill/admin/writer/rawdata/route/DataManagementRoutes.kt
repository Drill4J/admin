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
import com.epam.drill.admin.writer.rawdata.service.RawMethodBrowseService
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import kotlin.getValue

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
                    class Id(val parent: Builds, val buildId: String) {
                        @Resource("/raw-methods")
                        class RawMethods(val parent: Id) {
                            @Resource("/tree")
                            class Tree(val parent: RawMethods)

                            @Resource("/methods")
                            class Methods(
                                val parent: RawMethods,
                                val className: String,
                                val page: Int = 1,
                                val pageSize: Int = 100,
                            )
                        }
                    }
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

@Resource("method-ignore-rules")
class MethodIgnoreRulesRoute(
    val groupId: String? = null,
    val appId: String? = null,
    val page: Int = 1,
    val pageSize: Int = 20,
) {
    @Resource("/{id}")
    class Id(val parent: MethodIgnoreRulesRoute, val id: Int)
}

fun Route.dataManagementRoutes() {
    route("/data-management") {
        deleteBuildData()
        deleteTestSessionData()
    }
}

/** Browse method ignore rules and raw-method hierarchy (USER + ADMIN). */
fun Route.dataManagementReadRoutes() {
    route("/data-management") {
        getMethodIgnoreRules()
        getRawMethodTree()
        getRawMethods()
    }
}

/** Create/delete method ignore rules (ADMIN). */
fun Route.dataManagementWriteRoutes() {
    route("/data-management") {
        postMethodIgnoreRules()
        deleteMethodIgnoreRule()
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

fun Route.postMethodIgnoreRules() {
    val dataManagementService by closestDI().instance<DataManagementService>()

    post<MethodIgnoreRulesRoute> {
        dataManagementService.saveMethodIgnoreRule(call.decompressAndReceive())
        call.ok("Method ignore rule saved")
    }
}

fun Route.getMethodIgnoreRules() {
    val dataManagementService by closestDI().instance<DataManagementService>()

    get<MethodIgnoreRulesRoute> { params ->
        val groupId = requireNotNull(params.groupId) { "Query parameter 'groupId' is required" }
        val appId = requireNotNull(params.appId) { "Query parameter 'appId' is required" }
        call.ok(
            dataManagementService.getAllMethodIgnoreRules(
                groupId,
                appId,
                params.page,
                params.pageSize,
            )
        )
    }
}

fun Route.deleteMethodIgnoreRule() {
    val dataManagementService by closestDI().instance<DataManagementService>()

    delete<MethodIgnoreRulesRoute.Id> { params ->
        val groupId = requireNotNull(params.parent.groupId) { "Query parameter 'groupId' is required" }
        val appId = requireNotNull(params.parent.appId) { "Query parameter 'appId' is required" }
        dataManagementService.deleteMethodIgnoreRuleById(groupId, appId, params.id)
        call.ok("Method ignore rule deleted")
    }
}

fun Route.getRawMethodTree() {
    val service by closestDI().instance<RawMethodBrowseService>()

    get<Groups.Id.Apps.Id.Builds.Id.RawMethods.Tree> { params ->
        val build = params.parent.parent
        call.ok(
            service.getTree(
                build.parent.parent.parent.parent.groupId,
                build.parent.parent.appId,
                build.buildId,
            )
        )
    }
}

fun Route.getRawMethods() {
    val service by closestDI().instance<RawMethodBrowseService>()

    get<Groups.Id.Apps.Id.Builds.Id.RawMethods.Methods> { params ->
        val build = params.parent.parent
        call.ok(
            service.getMethods(
                build.parent.parent.parent.parent.groupId,
                build.parent.parent.appId,
                build.buildId,
                params.className,
                params.page,
                params.pageSize,
            )
        )
    }
}
