rootProject.name = "admin"

pluginManagement {
    val kotlinVersion: String by extra
    val licenseVersion: String by extra
    val shadowPluginVersion: String by extra
    val grgitVersion: String by extra
    val jibVersion: String by extra
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("multiplatform") version kotlinVersion
        kotlin("plugin.noarg") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("org.ajoberstar.grgit") version grgitVersion
        id("com.github.hierynomus.license") version licenseVersion
        id("com.github.johnrengelman.shadow") version shadowPluginVersion
        id("com.google.cloud.tools.jib") version jibVersion
    }
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

val sharedLibsLocalPath: String by extra
val includeSharedLib: Settings.(String) -> Unit = {
    include(it)
    project(":$it").projectDir = file(sharedLibsLocalPath).resolve(it)
}

includeSharedLib("common")
includeSharedLib("plugin-api-admin")
includeSharedLib("test2code-api")
includeSharedLib("test2code-common")
include("admin-auth")
include("admin-writer")
include("admin-app")