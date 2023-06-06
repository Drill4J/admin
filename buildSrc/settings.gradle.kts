pluginManagement {
    val kotlinVersion: String by extra
    val licenseVersion: String by extra
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("multiplatform") version kotlinVersion
        id("com.github.hierynomus.license") version licenseVersion
    }
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

include("kni-runtime")
include("kni-plugin")
project(":kni-runtime").projectDir = file("../lib-jvm-shared/kni-runtime")
project(":kni-plugin").projectDir = file("../lib-jvm-shared/kni-plugin")
