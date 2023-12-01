package com.epam.drill.admin.auth.config

import io.ktor.config.*

/**
 * A configuration for UI.
 * @param config the Ktor configuration
 */
class UIConfig(private val config: ApplicationConfig) {
    /**
     * UI application root URL. The key is "drill.ui.rootUrl".
     */
    val uiRootUrl: String
        get() = config.property("rootUrl").getString()

    /**
     * UI application root path. The key is "drill.ui.rootPath". Optional, "/" by default.
     */
    val uiRootPath: String
        get() = config.propertyOrNull("rootPath")?.getString() ?: "/"
}