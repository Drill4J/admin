rootProject.name = "admin"

pluginManagement {
    val kotlinVersion: String by extra
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("com.google.cloud.tools.jib") version "1.7.0"
        id("com.github.johnrengelman.shadow") version "5.1.0"

        repositories {
            gradlePluginPortal()
            maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
        }

    }
}

include(":core")
include(":test-framework")
include(":test-framework:test-data")
include(":test-framework:test-plugin")
include(":test-framework:test-plugin:admin-part")
include(":test-framework:test-plugin:agent-part")
include(":tests")
