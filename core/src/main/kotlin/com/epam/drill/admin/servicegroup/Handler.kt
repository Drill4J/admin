package com.epam.drill.admin.servicegroup

import com.epam.drill.router.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import org.kodein.di.*
import org.kodein.di.generic.*


class ServiceGroupHandler(override val kodein: Kodein) : KodeinAware {

    private val app: Application by instance()
    private val serviceGroupManager: ServiceGroupManager by instance()

    init {
        app.routing {
            authenticate {
                val meta = "Update service group"
                    .examples(
                        example(
                            "serviceGroup",
                            ServiceGroup(
                                id = "some-group",
                                name = "Some Group"
                            )
                        )
                    ).responds(
                        ok<Unit>(),
                        notFound()
                    )
                put<Routes.Api.ServiceGroup.Update, ServiceGroup>(meta) { _, group ->
                    val statusCode = when (serviceGroupManager.update(group)) {
                        null -> HttpStatusCode.NotFound
                        else -> HttpStatusCode.OK
                    }
                    call.respond(statusCode)
                }
            }
        }
    }
}
