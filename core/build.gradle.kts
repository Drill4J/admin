plugins {
    kotlin("jvm")
    id("kotlinx-atomicfu")
    kotlin("plugin.serialization")
    application
    `maven-publish`
    id("com.google.cloud.tools.jib")
    id("com.github.johnrengelman.shadow")
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

val drillApiVersion: String by extra
val drillLogger: String by extra
val serializationVersion: String by extra
val collectionImmutableVersion: String by extra
val ktorVersion: String by extra
val kodeinVersion: String by extra
val swaggerVersion: String by extra
val koduxVersion: String by extra
val xodusVersion: String by extra
val zstdJniVersion: String by extra
val cacheMapDB: String by extra

val junitVersion: String by extra
val mockkVersion: String by extra

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
    implementation("com.epam.drill:drill-admin-part:$drillApiVersion")
    implementation("com.epam.drill:common:$drillApiVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:$collectionImmutableVersion")
    implementation("org.kodein.di:kodein-di-generic-jvm:$kodeinVersion")
    implementation("com.epam.drill.logger:logger:$drillLogger")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.epam.drill:kodux:$koduxVersion")
    implementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")
    implementation("com.epam.drill.ktor:ktor-swagger:$swaggerVersion")
    implementation("com.github.luben:zstd-jni:$zstdJniVersion")
    implementation("org.mapdb:mapdb:$cacheMapDB")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}

val appMainClassName by extra("io.ktor.server.netty.EngineMain")

val defaultAppJvmArgs = listOf(
    "-server",
    "-Djava.awt.headless=true",
    "-XX:+UseG1GC",
    "-XX:+UseStringDeduplication",
    "-XX:MaxDirectMemorySize=5G"
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
        image = "drill4j/admin"
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
                "-Xuse-experimental=kotlinx.serialization.ExperimentalSerializationApi",
                "-Xuse-experimental=kotlinx.serialization.InternalSerializationApi",
                "-Xuse-experimental=io.ktor.locations.KtorExperimentalLocationsAPI",
                "-Xuse-experimental=io.ktor.util.KtorExperimentalAPI",
                "-Xuse-experimental=io.ktor.util.InternalAPI",
                "-Xuse-experimental=kotlin.Experimental",
                "-Xuse-experimental=kotlin.ExperimentalStdlibApi",
                "-Xuse-experimental=kotlin.time.ExperimentalTime",
                "-Xuse-experimental=kotlinx.coroutines.ObsoleteCoroutinesApi",
                "-Xuse-experimental=kotlinx.serialization.ImplicitReflectionSerializer"
            )
        }
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}

publishing {
    repositories {
        maven {
            url = uri("http://oss.jfrog.org/oss-release-local")
            credentials {
                username =
                    if (project.hasProperty("bintrayUser"))
                        project.property("bintrayUser").toString()
                    else System.getenv("BINTRAY_USER")
                password =
                    if (project.hasProperty("bintrayApiKey"))
                        project.property("bintrayApiKey").toString()
                    else System.getenv("BINTRAY_API_KEY")
            }
        }
    }

    publications {
        create<MavenPublication>("adminFull") {
            artifact(tasks.shadowJar.get())
            artifactId = "admin-core"
        }
        create<MavenPublication>("admin") {
            artifact(tasks.jar.get())
            artifactId = "admin-core"
            artifact(sourcesJar.get())
        }
    }
}
