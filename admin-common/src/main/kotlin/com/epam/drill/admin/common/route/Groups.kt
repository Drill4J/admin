package com.epam.drill.admin.common.route

import io.ktor.resources.Resource

@Resource("/groups")
class Groups {
    @Resource("/{groupId}")
    class GroupId(
        val parent: Groups,
        val groupId: String
    )
}