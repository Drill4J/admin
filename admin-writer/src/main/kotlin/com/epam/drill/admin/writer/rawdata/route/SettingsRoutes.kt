package com.epam.drill.admin.writer.rawdata.route

import com.epam.drill.admin.common.route.ok
import com.epam.drill.admin.writer.rawdata.service.SettingsService
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.resources.get
import io.ktor.server.resources.put
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

@Resource("group-settings")
class GroupSettingsRoute {
    @Resource("/{groupId}")
    class GroupId(val parent: GroupSettingsRoute, val groupId: String)
}

fun Route.settingsRoutes() {
    getGroupSettings()
    putGroupSettings()
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