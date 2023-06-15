import java.util.Properties
import java.nio.file.Paths

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

val sharedLibsLocal = rootDir.parentFile.resolve("gradle.properties").reader().use {
    val path = Properties().run {
        load(it)
        getProperty("sharedLibsLocalPath")
    }
    if(Paths.get(path).isAbsolute) {
        file(path)
    }
    else {
        rootDir.parentFile.resolve(path)
    }
}

include("kni-runtime")
include("kni-plugin")
project(":kni-runtime").projectDir = sharedLibsLocal.resolve("kni-runtime")
project(":kni-plugin").projectDir = sharedLibsLocal.resolve("kni-plugin")
