package com.epam.drill.admin.endpoints.system

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import org.kodein.di.*
import org.kodein.di.generic.*

class InfoController(override val kodein: Kodein) : KodeinAware {
    private val app: Application by instance()

    init {
        app.routing {
            get("/application-info") {
                call.respondBytes(
                    InfoController::class.java.getResourceAsStream("/version.json").readBytes(),
                    ContentType.Application.Json
                )
            }
        }
    }
}
