plugins {
    kotlin("jvm") apply false
    id("kotlinx-atomicfu") apply false
    kotlin("plugin.serialization") apply false
    id("com.github.hierynomus.license")
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
    apply(from = rootProject.uri("$scriptUrl/maven-repo.gradle.kts"))
}

subprojects {
    repositories {
        mavenLocal()
        apply(from = "$scriptUrl/maven-repo.gradle.kts")
        jcenter()
    }
}

val licenseFormatSettings by tasks.registering(com.hierynomus.gradle.license.tasks.LicenseFormat::class) {
    source = fileTree(project.projectDir).also {
        include("**/*.kt", "**/*.java", "**/*.groovy")
        exclude("**/.idea")
    }.asFileTree
    headerURI = java.net.URI("https://raw.githubusercontent.com/Drill4J/drill4j/develop/COPYRIGHT")
}

tasks["licenseFormat"].dependsOn(licenseFormatSettings)
