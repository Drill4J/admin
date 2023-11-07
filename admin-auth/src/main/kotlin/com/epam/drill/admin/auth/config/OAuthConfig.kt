package com.epam.drill.admin.auth.config

import io.ktor.application.*
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

class OAuthConfig(override val di: DI) : DIAware {
    private val app by instance<Application>()

}