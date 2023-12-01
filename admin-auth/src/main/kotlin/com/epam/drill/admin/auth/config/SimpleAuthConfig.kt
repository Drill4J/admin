package com.epam.drill.admin.auth.config

import io.ktor.config.*
import java.lang.Boolean.parseBoolean

/**
 * A configuration for simple authentication.
 * @param config the Ktor configuration
 */
class SimpleAuthConfig(
    private val config: ApplicationConfig
) {

    /**
     * A flag indicating whether the sign-up is enabled. Optional, true by default.
     */
    val signUpEnabled: Boolean
        get() = config.propertyOrNull("signUpEnabled")?.getString()?.let { parseBoolean(it) } ?: true
}