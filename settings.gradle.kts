rootProject.name = "admin"

pluginManagement {
    val kotlinVersion: String by extra
    val atomicfuVersion: String by extra
    val licenseVersion: String by extra
    val shadowPluginVersion: String by extra
    val grgitVersion: String by extra
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("multiplatform") version kotlinVersion
        kotlin("plugin.noarg") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("kotlinx-atomicfu") version atomicfuVersion
        id("org.ajoberstar.grgit") version grgitVersion
        id("com.github.hierynomus.license") version licenseVersion
        id("com.github.johnrengelman.shadow") version shadowPluginVersion
    }
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy.eachPlugin {
        if(requested.id.id == "kotlinx-atomicfu") useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${target.version}")
    }
}

val includeSharedLib: Settings.(String) -> Unit = {
    include(it)
    project(":$it").projectDir = file("lib-jvm-shared/$it")
}

includeSharedLib("admin-analytics")
includeSharedLib("common")
includeSharedLib("dsm")
includeSharedLib("dsm-annotations")
includeSharedLib("dsm-test-framework")
includeSharedLib("kni-runtime")
includeSharedLib("ktor-swagger")
includeSharedLib("jvmapi")
includeSharedLib("logger")
includeSharedLib("logger-api")
includeSharedLib("logger-test-agent")
includeSharedLib("plugin-api-admin")
includeSharedLib("plugin-api-agent")
includeSharedLib("test-data")
includeSharedLib("test-plugin")
includeSharedLib("test-plugin-admin")
includeSharedLib("test-plugin-agent")
include("admin-core")
include("test-framework")
include("tests")
