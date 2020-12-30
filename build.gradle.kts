plugins {
    kotlin("jvm") apply false
    id("kotlinx-atomicfu") apply false
    kotlin("plugin.serialization") apply false
    base
}

//TODO remove this block and gradle/classes dir after gradle is updated to v6.8
buildscript {
    dependencies {
        classpath(files("gradle/classes"))
    }
}

val scriptUrl: String by extra

allprojects {
    apply(from = rootProject.uri("$scriptUrl/git-version.gradle.kts"))
}

subprojects {
    repositories {
        mavenLocal()
        apply(from = "$scriptUrl/maven-repo.gradle.kts")
        jcenter()
    }
}
