import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat
import com.google.cloud.tools.jib.gradle.JibTask

@Suppress("RemoveRedundantBackticks")
plugins {
    `application`
    `maven-publish`
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
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
val mapdbVersion: String by parent!!.extra
val flywaydbVersion: String by parent!!.extra
val postgresEmbeddedVersion: String by parent!!.extra

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
    implementation("org.mapdb:mapdb:$mapdbVersion") {
        exclude(group = "org.eclipse.collections", module = "eclipse-collections")
        exclude(group = "org.eclipse.collections", module = "eclipse-collections-api")
        exclude(group = "org.eclipse.collections", module = "eclipse-collections-forkjoin")
    }
    implementation("org.eclipse.collections:eclipse-collections:11.1.0")
    implementation("org.eclipse.collections:eclipse-collections-api:11.1.0")
    implementation("org.eclipse.collections:eclipse-collections-forkjoin:11.1.0")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("ru.yandex.qatools.embed:postgresql-embedded:$postgresEmbeddedVersion")

    implementation(project(":admin-analytics"))
    implementation(project(":common"))
    implementation(project(":plugin-api-admin"))
    implementation(project(":dsm"))
    implementation(project(":ktor-swagger"))
    implementation(project(":test2code-admin"))
    implementation(project(":test2code-api"))

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

val fullImageTag = "ghcr.io/drill4j/admin"
val apiPort = "8090"
val debugPort = "5006"
val secureApiPort = "8453"
val jibExtraDirs = "$buildDir/jib-extra-dirs"
val gitUsername = System.getenv("GH_USERNAME") ?: ""
val gitPassword = System.getenv("GH_TOKEN") ?: ""
jib {
    from {
        image = "adoptopenjdk/openjdk11:latest"
    }
    to {
        image = fullImageTag
        tags = setOf(version.toString())
        auth {
            username=gitUsername
            password=gitPassword
        }
    }
    container {
        ports = listOf(apiPort, debugPort , secureApiPort)
        volumes = listOf("/distr", "/work")
        mainClass = jarMainClassName
        jvmFlags = defaultJvmArgs
    }
    extraDirectories {
        setPaths(jibExtraDirs)
        permissions = mapOf("/distr" to "775", "/work" to "775")
    }
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
    val sourcesJar by registering(Jar::class) {
        from(sourceSets.main.get().allSource)
        archiveClassifier.set("sources")
    }
    val cleanData by registering(Delete::class) {
        group = "build"
        delete("distr")
        delete("work")
    }
    clean.get().dependsOn(cleanData)
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
    val processJibExtraDirs by registering(Copy::class) {
        group = "jib"
        from("src/main/jib")
        into(jibExtraDirs)
        outputs.upToDateWhen(Specs.satisfyNone())
        doLast {
            listOf("distr", "work").map(destinationDir::resolve).forEach { mkdir(it) }
        }
    }
    withType<JibTask> {
        dependsOn(processJibExtraDirs)
    }
    val createWindowsDockerImage by registering(Exec::class) {
        dependsOn(assemble)
        workingDir(projectDir)
        commandLine("docker login -u $gitUsername -p $gitPassword")
        commandLine(
            "docker", "build",
            "--build-arg", "ADMIN_VERSION=$version",
            "--build-arg", "API_PORT=$apiPort",
            "--build-arg", "DEBUG_PORT=$debugPort",
            "--build-arg", "SECURE_API_PORT=$secureApiPort",
            "--build-arg", "JVM_ARGS=${defaultJvmArgs.joinToString(" ")}",
            "--build-arg", "MAIN_CLASS_NAME=$jarMainClassName",
            "-t", "$fullImageTag:$version-win",
            "."
        )
        commandLine("docker images")
        commandLine(
            "docker", "push", "$version-win"
        )
        println("After-push")
    }
}

publishing {
    publications.create<MavenPublication>("admin-core") {
        from(components["java"])
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
