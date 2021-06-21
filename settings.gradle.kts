rootProject.name = "admin"

pluginManagement {
    val kotlinVersion: String by extra
    val atomicFuVersion: String by extra
    val licenseVersion: String by extra
    val kotlinNoarg: String by extra
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("kotlinx-atomicfu") version atomicFuVersion
        id("com.google.cloud.tools.jib") version "1.7.0"
        id("com.github.johnrengelman.shadow") version "5.1.0"
        id("com.github.hierynomus.license") version licenseVersion
        id("org.jetbrains.kotlin.plugin.noarg") version kotlinNoarg
        repositories {
            gradlePluginPortal()
            maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
        }
    }
}

//TODO remove after plugin marker is added to the atomicfu artifacts
mapOf(
    "kotlinx-atomicfu" to "org.jetbrains.kotlinx:atomicfu-gradle-plugin"
).let { substitutions ->
    pluginManagement.resolutionStrategy.eachPlugin {
        substitutions["${requested.id}"]?.let { useModule("$it:${target.version}") }
    }
}

include(":core")
include(":test-framework")
include(":test-framework:test-data")
include(":test-framework:test-plugin")
include(":test-framework:test-plugin:admin-part")
include(":test-framework:test-plugin:agent-part")
include(":tests")
