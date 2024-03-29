rootProject.name = "admin"

pluginManagement {
    val kotlinVersion: String by extra
    val atomicfuVersion: String by extra
    val licenseVersion: String by extra
    val shadowPluginVersion: String by extra
    val grgitVersion: String by extra
    val jibVersion: String by extra
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("multiplatform") version kotlinVersion
        kotlin("plugin.noarg") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("kotlinx-atomicfu") version atomicfuVersion
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
    resolutionStrategy.eachPlugin {
        if(requested.id.id == "kotlinx-atomicfu") useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${target.version}")
    }
}

val sharedLibsLocalPath: String by extra
val includeSharedLib: Settings.(String) -> Unit = {
    include(it)
    project(":$it").projectDir = file(sharedLibsLocalPath).resolve(it)
}

includeSharedLib("admin-analytics")
includeSharedLib("common")
includeSharedLib("dsm")
includeSharedLib("dsm-annotations")
includeSharedLib("dsm-test-framework")
includeSharedLib("kt2dts")
includeSharedLib("kt2dts-cli")
includeSharedLib("kt2dts-api-sample")
includeSharedLib("ktor-swagger")
includeSharedLib("plugin-api-admin")
includeSharedLib("test-data")
includeSharedLib("test-plugin")
includeSharedLib("test-plugin-admin")
includeSharedLib("test-plugin-agent")
includeSharedLib("test2code-api")
includeSharedLib("test2code-api")
includeSharedLib("test2code-common")
include("admin-auth")
include("test2code-admin")
include("admin-core")
include("test-framework")
include("tests")
