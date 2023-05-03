import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat

@Suppress("RemoveRedundantBackticks")
plugins {
    `application`
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
    id("com.github.hierynomus.license")
    id("com.github.johnrengelman.shadow")
}

group = "com.epam.drill"
version = rootProject.version

val kotlinxCollectionsVersion: String by extra
val kotlinxSerializationVersion: String by extra
val ktorVersion: String by extra
val kodeinVersion: String by extra
val microutilsLoggingVersion: String by extra
val lubenZstdVersion: String by extra
val mapdbVersion: String by extra
val flywaydbVersion: String by extra
val postgresEmbeddedVersion: String by extra

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
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:$kotlinxCollectionsVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinxSerializationVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.github.microutils:kotlin-logging-jvm:$microutilsLoggingVersion")
    implementation("org.kodein.di:kodein-di-jvm:$kodeinVersion")
    implementation("org.flywaydb:flyway-core:$flywaydbVersion")
    implementation("org.mapdb:mapdb:$mapdbVersion")
    implementation("com.github.luben:zstd-jni:$lubenZstdVersion")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("ru.yandex.qatools.embed:postgresql-embedded:$postgresEmbeddedVersion")

    implementation(project(":admin-analytics"))
    implementation(project(":common"))
    implementation(project(":logger"))
    implementation(project(":plugin-api-admin"))
    implementation(project(":dsm"))
    implementation(project(":ktor-swagger"))

    api(project(":dsm-annotations"))

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("io.mockk:mockk:1.9.3")

    testImplementation(project(":dsm-test-framework"))
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

val jarMainClassName: String by extra("io.ktor.server.netty.EngineMain")
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

@Suppress("UNUSED_VARIABLE")
tasks {
    processResources {
        val adminVersion = file("$buildDir/tmp/admin.version").apply {
            parentFile.mkdirs()
            writeText(project.version.toString())
        }
        from(adminVersion) {
            into("META-INF/drill")
        }
    }
    test {
        useJUnitPlatform()
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
    val cleanData by registering(Delete::class) {
        group = "build"
        delete("distr")
        delete("work")
    }
    clean.get().dependsOn(cleanData)
    val sourcesJar by registering(Jar::class) {
        from(sourceSets.main.get().allSource)
        archiveClassifier.set("sources")
    }
    (run) {
        environment("DRILL_DEVMODE", true)
        environment("DRILL_DEFAULT_PACKAGES", "org/springframework/samples/petclinic")
        systemProperty("analytic.disable", true)
        mustRunAfter(cleanData)
    }
    val firstRun by registering {
        group = "application"
        dependsOn(cleanData, run)
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
