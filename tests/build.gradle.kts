plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val testBuilds = listOf("build1", "build2")
sourceSets {
    testBuilds.forEach { create(it) }
}

val testData: Configuration by configurations.creating {}

configurations {
    testImplementation {
        extendsFrom(testData)
    }
    testBuilds.forEach {
        named("${it}Implementation") {
            extendsFrom(testData)
        }
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testImplementation(project(":core"))
    testImplementation(project(":test-framework"))

    testImplementation("com.epam.drill:drill-admin-part-jvm:$drillApiVersion")
    testImplementation("com.epam.drill:common-jvm:$drillApiVersion")

    testImplementation("org.kodein.di:kodein-di-generic-jvm:6.2.0")
    testImplementation("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")

    testImplementation("com.epam.drill:drill-agent-part-jvm:$drillApiVersion")

    testImplementation("com.epam.drill:ktor-swagger:$swaggerVersion")
    testImplementation(ktor("server-test-host"))
    testImplementation(ktor("auth"))
    testImplementation(ktor("auth-jwt"))
    testImplementation(ktor("server-netty"))
    testImplementation(ktor("locations"))
    testImplementation(ktor("server-core"))
    testImplementation(ktor("websockets"))
    testImplementation(ktor("client-cio"))
    testImplementation(ktor("serialization"))

    testImplementation("com.epam.drill:kodux-jvm:$koduxVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    testImplementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")

    testImplementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    testImplementation("org.apache.bcel:bcel:$bcelVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3")

    testImplementation(kotlin("test-junit5"))
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("io.mockk:mockk:1.9.3")

    testData(project(":test-framework:test-data"))
}

tasks {
    clean {
        delete("work", "distr")
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            allWarningsAsErrors = true
            freeCompilerArgs = listOf(
                "-Xuse-experimental=kotlin.Experimental",
                "-Xuse-experimental=kotlin.time.ExperimentalTime",
                "-Xuse-experimental=io.ktor.locations.KtorExperimentalLocationsAPI"
            )
        }
    }

    val testPluginProject = project(":test-framework:test-plugin")

    val testBuildClassesTasks = testBuilds.map { named("${it}Classes") }

    val prepareDist by registering(Copy::class) {
        val distZip by testPluginProject.tasks
        from(distZip)
        into(file("distr").resolve("adminStorage"))
    }

    val integrationTest by registering(Test::class) {
        description = "Runs integration tests"
        group = "verification"
        dependsOn(testBuildClassesTasks)
        dependsOn(prepareDist)
        mustRunAfter(test)
        useJUnitPlatform()
        systemProperty("plugin.config.path", testPluginProject.projectDir.resolve("plugin_config.json"))
    }

    check {
        dependsOn(integrationTest)
    }
}

@Suppress("unused")
fun DependencyHandler.ktor(module: String, version: String? = ktorVersion): Any =
    "io.ktor:ktor-$module:${version ?: "+"}"
