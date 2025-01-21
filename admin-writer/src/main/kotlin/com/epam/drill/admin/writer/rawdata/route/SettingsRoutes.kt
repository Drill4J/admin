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

import com.epam.drill.admin.common.route.ok
import com.epam.drill.admin.writer.rawdata.service.SettingsService
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.get
import io.ktor.server.resources.put
import io.ktor.server.resources.post
import io.ktor.server.resources.delete
import io.ktor.server.routing.*
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

@Resource("group-settings")
class GroupSettingsRoute {
    @Resource("/{groupId}")
    class GroupId(val parent: GroupSettingsRoute, val groupId: String)
}

@Resource("method-ignore-rules")
class MethodIgnoreRulesRoute() {
    @Resource("/{groupId}/{appId}")
    class GroupIdAppId(val parent: MethodIgnoreRulesRoute, val groupId: String, val appId: String) {
        @Resource("/{id}")
        class Id(val parent: GroupIdAppId, val id: Int)
    }
}

fun Route.settingsRoutes() {
    getGroupSettings()
    putGroupSettings()
    deleteGroupSettings()
    getMethodIgnoreRules()
    postMethodIgnoreRules()
    deleteMethodIgnoreRule()
}

fun Route.getGroupSettings() {
    val settingsService by closestDI().instance<SettingsService>()

    get<GroupSettingsRoute.GroupId> { params ->
        val groupSettingsView = settingsService.getGroupSettings(params.groupId)
        call.ok(groupSettingsView)
    }
}

fun Route.putGroupSettings() {
    val settingsService by closestDI().instance<SettingsService>()

    put<GroupSettingsRoute.GroupId> { params ->
        settingsService.saveGroupSettings(params.groupId, call.receive())
        call.ok("Group settings saved")
    }
}

fun Route.deleteGroupSettings() {
    val settingsService by closestDI().instance<SettingsService>()

    delete<GroupSettingsRoute.GroupId> { params ->
        settingsService.clearGroupSettings(params.groupId)
        call.ok("Group settings have been reset to default values")
    }
}


fun Route.postMethodIgnoreRules() {
    val settingsService by closestDI().instance<SettingsService>()

    post<MethodIgnoreRulesRoute.GroupIdAppId> { params ->
        settingsService.saveMethodIgnoreRule(params.groupId, params.appId, call.decompressAndReceive())
        call.ok("Method ignore rule saved")
    }
}

fun Route.getMethodIgnoreRules() {
    val settingsService by closestDI().instance<SettingsService>()

    get<MethodIgnoreRulesRoute.GroupIdAppId> { params ->
        call.ok(settingsService.getMethodIgnoreRules(params.groupId, params.appId))
    }
}

fun Route.deleteMethodIgnoreRule() {
    val settingsService by closestDI().instance<SettingsService>()

    delete<MethodIgnoreRulesRoute.GroupIdAppId.Id> { params ->
        val groupId = params.parent.groupId
        val appId = params.parent.appId
        val ruleId = params.id
        settingsService.deleteMethodIgnoreRule(groupId, appId, ruleId)
        call.ok("Method ignore rule deleted")
    }
}
