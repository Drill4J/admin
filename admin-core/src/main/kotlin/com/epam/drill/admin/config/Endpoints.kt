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