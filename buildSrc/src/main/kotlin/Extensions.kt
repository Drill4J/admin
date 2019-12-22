@file:Suppress("unused")

import org.gradle.api.artifacts.dsl.DependencyHandler

fun DependencyHandler.ktor(module: String, version: String? = ktorVersion): Any =
    listOfNotNull("io.ktor:ktor-$module", version).joinToString(separator = ":")

fun DependencyHandler.drill(module: String, version: String? = null): Any =
    listOfNotNull("com.epam.drill", module, version).joinToString(separator = ":")
