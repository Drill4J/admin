@Suppress("RemoveRedundantBackticks")
plugins {
    `application`
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
    id("com.google.cloud.tools.jib")
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin.sourceSets.main {
    kotlin.srcDir(
        file("src/generated/kotlin").apply {
            mkdirs()
            resolve("Version.kt").writeText(
                "package com.epam.drill.admin internal val adminVersion = \"${rootProject.version}\""
            )
        }
    )
}

val kotlinxCollectionsVersion: String by extra
val kotlinxSerializationVersion: String by extra
val hikariVersion: String by project
val ktorVersion: String by extra
val kodeinVersion: String by extra
val microutilsLoggingVersion: String by extra
val lubenZstdVersion: String by extra
val mapdbVersion: String by extra
val flywaydbVersion: String by extra

val junitVersion: String by extra
val mockkVersion: String by extra
val postgresEmbeddedVersion: String by extra
val testContainerVersion: String by project

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:$kotlinxCollectionsVersion")
    implementation("org.kodein.di:kodein-di-jvm:$kodeinVersion")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("org.flywaydb:flyway-core:$flywaydbVersion")
    implementation("ru.yandex.qatools.embed:postgresql-embedded:$postgresEmbeddedVersion")
    implementation("io.github.microutils:kotlin-logging-jvm:$microutilsLoggingVersion")
    implementation("com.github.luben:zstd-jni:$lubenZstdVersion")
    implementation("org.mapdb:mapdb:$mapdbVersion")

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

val appMainClassName by extra("io.ktor.server.netty.EngineMain")

val defaultAppJvmArgs = listOf(
    "-server",
    "-Djava.awt.headless=true",
    "-XX:+UseG1GC",
    "-XX:+UseContainerSupport",
    "-XX:+UseStringDeduplication",
    "-XX:MaxDirectMemorySize=2G"
)

val devJvmArgs = listOf(
    "-Xms128m",
    "-Xmx2g",
    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006"
)

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

application {
    mainClassName = appMainClassName
    applicationDefaultJvmArgs = defaultAppJvmArgs + devJvmArgs
}

val jibExtraDirs = "$buildDir/jib-extra-dirs"

jib {
    from {
        image = "adoptopenjdk/openjdk11:latest"
    }
    to {
        image = "ghcr.io/drill4j/admin"
        tags = setOf("${project.version}")
    }
    container {
        ports = listOf("8090", "5006")
        mainClass = appMainClassName
        volumes = listOf("/work", "/distr")

        jvmFlags = defaultAppJvmArgs
    }
    extraDirectories {
        setPaths(jibExtraDirs)
        permissions = mapOf("/work" to "775", "/distr" to "775")
    }
}

tasks {
    val cleanData by registering(Delete::class) {
        group = "build"
        delete("work", "distr")
    }

    clean {
        dependsOn(cleanData)
    }

    processResources {
        from(provider {
            file("$buildDir/tmp/admin.version").apply {
                parentFile.mkdirs()
                writeText("${rootProject.version}")
            }
        }) { into("META-INF/drill") }
    }

    (run) {
        environment("DRILL_DEVMODE", true)
        environment("DRILL_DEFAULT_PACKAGES", "org/springframework/samples/petclinic")
        systemProperty("analytic.disable", true)
        mustRunAfter(cleanData)
    }

    register("firstRun") {
        group = "application"
        dependsOn(cleanData, run)
    }

    test {
        useJUnitPlatform()
    }

    val makeJibExtraDirs by registering(Copy::class) {
        group = "jib"
        outputs.upToDateWhen(Specs.satisfyNone())
        into(jibExtraDirs)
        from("src/main/jib")
        from("temporary.jks")
        doLast {
            listOf("work", "distr")
                .map(destinationDir::resolve)
                .forEach { mkdir(it) }
        }
    }

    withType<com.google.cloud.tools.jib.gradle.JibTask> {
        dependsOn(makeJibExtraDirs)
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi",
                "-Xopt-in=kotlinx.serialization.InternalSerializationApi",
                "-Xopt-in=io.ktor.locations.KtorExperimentalLocationsAPI",
                "-Xopt-in=io.ktor.util.InternalAPI",
                "-Xopt-in=kotlin.Experimental",
                "-Xopt-in=kotlin.ExperimentalStdlibApi",
                "-Xopt-in=kotlin.time.ExperimentalTime",
                "-Xopt-in=kotlinx.coroutines.ObsoleteCoroutinesApi",
            )
        }
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}
