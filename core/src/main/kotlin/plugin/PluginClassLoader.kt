package com.epam.drill.admin.plugin

import mu.*
import java.net.*


private val logger = KotlinLogging.logger {}

class PluginClassLoader(url: URL) : URLClassLoader(arrayOf(url)) {

    override fun findClass(name: String?): Class<*> {
        logger.trace { "Loading $name" }
        return super.findClass(name)
    }
}
