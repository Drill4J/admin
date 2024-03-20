package com.epam.drill.admin.route

import com.epam.drill.admin.auth.route.ok
import io.ktor.application.*
import io.ktor.locations.*
import io.ktor.routing.*

@Location("/")
object Root

fun Route.rootRoute() {
    get<Root> {
        call.ok("Drill4J Admin Backend")
    }
}