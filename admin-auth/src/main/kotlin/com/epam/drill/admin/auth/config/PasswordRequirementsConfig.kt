package com.epam.drill.admin.auth.config

import io.ktor.application.*
import io.ktor.config.*
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

class PasswordRequirementsConfig(override val di: DI) : DIAware {
    private val app by instance<Application>()
    private val jwt: ApplicationConfig
        get() = app.environment.config.config("drill").config("passwordRequirements")

    val minLength: Int
        get() = jwt.propertyOrNull("minLength")?.getString()?.toInt() ?: 6

    val mustHaveUppercase: Boolean
        get() = jwt.propertyOrNull("mustHaveUppercase")?.getString()?.toBoolean() ?: false

    val mustHaveLowercase: Boolean
        get() = jwt.propertyOrNull("mustHaveLowercase")?.getString()?.toBoolean() ?: false

    val mustHaveDigit: Boolean
        get() = jwt.propertyOrNull("mustHaveDigit")?.getString()?.toBoolean() ?: false
}