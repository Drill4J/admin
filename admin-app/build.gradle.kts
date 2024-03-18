import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat

plugins {
    application
    `maven-publish`
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.hierynomus.license")
    id("com.github.johnrengelman.shadow")
    id("com.google.cloud.tools.jib")
}

group = "com.epam.drill"
version = rootProject.version

val kotlinxCollectionsVersion: String by parent!!.extra
val kotlinxSerializationVersion: String by parent!!.extra
val ktorVersion: String by parent!!.extra
val kodeinVersion: String by parent!!.extra
val microutilsLoggingVersion: String by parent!!.extra
val zaxxerHikaricpVersion: String by parent!!.extra
val postgresSqlVersion: String by parent!!.extra

repositories {
    mavenLocal()
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinxSerializationVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.github.microutils:kotlin-logging-jvm:$microutilsLoggingVersion")
    implementation("org.kodein.di:kodein-di-framework-ktor-server-jvm:$kodeinVersion")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.zaxxer:HikariCP:$zaxxerHikaricpVersion")
    implementation("org.postgresql:postgresql:$postgresSqlVersion")

    implementation(project(":admin-auth"))
    implementation(project(":admin-writer"))

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
}

kotlin.sourceSets {
    all {
        languageSettings.optIn("kotlin.Experimental")
        languageSettings.optIn("kotlin.ExperimentalStdlibApi")
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
        languageSettings.optIn("kotlinx.coroutines.ObsoleteCoroutinesApi")
        languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        languageSettings.optIn("kotlinx.serialization.InternalSerializationApi")
        languageSettings.optIn("io.ktor.locations.KtorExperimentalLocationsAPI")
        languageSettings.optIn("io.ktor.util.InternalAPI")
    }
    main {
        kotlin.srcDir("src/commonGenerated/kotlin")
        file("src/commonGenerated/kotlin/com/epam/drill/admin").apply {
            mkdirs()
            resolve("Version.kt").writeText("package com.epam.drill.admin\n\ninternal val adminVersion = \"${project.version}\"")
        }
    }
}

val jarMainClassName: String by parent!!.extra("io.ktor.server.netty.EngineMain")
val defaultJvmArgs = listOf(
    "-server",
    "-Djava.awt.headless=true",
    "-XX:+UseG1GC",
    "-XX:+UseStringDeduplication",
    "-XX:MaxDirectMemorySize=2G"
)
val devJvmArgs = listOf(
    "-Xms128m",
    "-Xmx2g",
    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006"
)

application {
    mainClass.set(jarMainClassName)
    applicationDefaultJvmArgs = defaultJvmArgs + devJvmArgs
}

val registryName = "debug"
val fullImageTag = "$registryName/drill4j/admin-app"
val apiPort = "8090"
val debugPort = "5006"
val secureApiPort = "8453"
val gitUsername = System.getenv("GH_USERNAME") ?: ""
val gitPassword = System.getenv("GH_TOKEN") ?: ""

@Suppress("UNUSED_VARIABLE")
tasks {
    test {
        useJUnitPlatform()
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
    val sourcesJar by registering(Jar::class) {
        from(sourceSets.main.get().allSource)
        archiveClassifier.set("sources")
    }
}

@Suppress("UNUSED_VARIABLE")
license {
    headerURI = URI("https://raw.githubusercontent.com/Drill4J/drill4j/develop/COPYRIGHT")
    val licenseMain by tasks.getting(LicenseCheck::class) {
        source = fileTree("$projectDir/src/main").also {
            include("**/*.kt", "**/*.java", "**/*.groovy")
        }
    }
    val licenseFormatMain by tasks.getting(LicenseFormat::class) {
        source = fileTree("$projectDir/src/main").also {
            include("**/*.kt", "**/*.java", "**/*.groovy")
        }
    }
    val licenseFormatSources by tasks.registering(LicenseFormat::class) {
        source = fileTree("$projectDir/src").also {
            include("**/*.kt", "**/*.java", "**/*.groovy")
            exclude("**/commonGenerated")
        }
    }
    val licenseCheckSources by tasks.registering(LicenseCheck::class) {
        source = fileTree("$projectDir/src").also {
            include("**/*.kt", "**/*.java", "**/*.groovy")
            exclude("**/commonGenerated")
        }
    }
}