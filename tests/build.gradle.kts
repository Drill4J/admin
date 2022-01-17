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

val drillApiVersion: String by extra
val drillLogger: String by extra
val drillDsmVersion: String by extra

val serializationVersion: String by extra
val collectionImmutableVersion: String by extra
val ktorVersion: String by extra
val kodeinVersion: String by extra
val swaggerVersion: String by extra
val muLogger: String by extra
val zstdJniVersion: String by extra
val testContainerVersion: String by project

val junitVersion: String by extra
val mockkVersion: String by extra

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testImplementation(project(":core"))
    testImplementation(project(":test-framework"))

    testImplementation("com.epam.drill:drill-admin-part-jvm:$drillApiVersion")
    testImplementation("com.epam.drill:common-jvm:$drillApiVersion")

    testImplementation("org.kodein.di:kodein-di-jvm:$kodeinVersion")

    testImplementation("com.epam.drill:drill-agent-part-jvm:$drillApiVersion")

    testImplementation("com.epam.drill.ktor:ktor-swagger:$swaggerVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-auth:$ktorVersion")
    testImplementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
    testImplementation("io.ktor:ktor-locations:$ktorVersion")
    testImplementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-websockets:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-serialization:$ktorVersion")

    testImplementation("com.epam.drill.dsm:test-framework:$drillDsmVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    //TODO remove logging - EPMDJ-9548
    testImplementation("io.github.microutils:kotlin-logging-jvm:$muLogger")

    testImplementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:$collectionImmutableVersion")

    testImplementation(kotlin("test-junit5"))
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.2")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")

    testData(project(":test-framework:test-data"))
}

subprojects {
    repositories {
        mavenCentral()
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

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.Experimental",
                "-Xopt-in=kotlin.time.ExperimentalTime",
                "-Xopt-in=io.ktor.locations.KtorExperimentalLocationsAPI"
            )
        }
    }

    val testPluginProject = project(":test-framework:test-plugin")

    val testBuildClassesTasks = testBuilds.map { named("${it}Classes") }

    val prepareDist by registering(Sync::class) {
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
