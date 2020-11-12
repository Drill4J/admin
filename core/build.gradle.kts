plugins {
    kotlin("jvm")
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

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(ktor("auth"))
    implementation(ktor("auth-jwt"))
    implementation(ktor("server-netty"))
    implementation(ktor("locations"))
    implementation(ktor("server-core"))
    implementation(ktor("websockets"))
    implementation(ktor("client-cio"))
    implementation(ktor("serialization"))
    implementation(drill("drill-admin-part-jvm", drillApiVersion))
    implementation(drill("common-jvm", drillApiVersion))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:$collectionImmutableVersion")
    implementation("org.kodein.di:kodein-di-generic-jvm:$kodeinVersion")
    implementation("com.epam.drill.logger:logger:$drillLogger")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.epam.drill:kodux-jvm:$koduxVersion")
    implementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")
    implementation("com.epam.drill.ktor:ktor-swagger:$swaggerVersion")
    implementation("io.airlift:aircompressor:$aircompressorVersion")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}

val appMainClassName by extra("io.ktor.server.netty.EngineMain")

val defaultAppJvmArgs = listOf(
    "-server",
    "-Djava.awt.headless=true",
    "-XX:+UseG1GC",
    "-XX:+UseStringDeduplication"
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
        image = "gcr.io/distroless/java:8-debug"
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
