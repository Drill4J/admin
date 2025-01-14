rootProject.name = "admin"

pluginManagement {
    val kotlinVersion: String by extra
    val licenseVersion: String by extra
    val shadowPluginVersion: String by extra
    val grgitVersion: String by extra
    val jibVersion: String by extra
    val openApiGeneratorVersion: String by extra
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.noarg") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("org.ajoberstar.grgit") version grgitVersion
        id("com.github.hierynomus.license") version licenseVersion
        id("com.github.johnrengelman.shadow") version shadowPluginVersion
        id("com.google.cloud.tools.jib") version jibVersion
        id("org.openapi.generator") version openApiGeneratorVersion
    }
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

include("admin-common")
include("admin-auth")
include("admin-writer")
include("admin-metrics")
include("admin-app")