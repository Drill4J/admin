import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
    `maven-publish`
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
val cacheMapDB: String by extra
val testContainerVersion: String by project

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
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-server-test-host:$ktorVersion")
    implementation("com.epam.drill:drill-admin-part:$drillApiVersion")
    implementation("com.epam.drill:common:$drillApiVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serializationVersion")
    implementation("com.epam.drill.dsm:core:$drillDsmVersion")
    implementation("com.epam.drill.dsm:test-framework:$drillDsmVersion")
    implementation("io.github.microutils:kotlin-logging-jvm:$muLogger")
    implementation("org.kodein.di:kodein-di-jvm:$kodeinVersion")
    implementation("org.mapdb:mapdb:$cacheMapDB")
    implementation("io.mockk:mockk:$mockkVersion")
    api(project(":core"))
    implementation("org.apache.bcel:bcel:6.3.1")
    implementation("com.epam.drill:drill-agent-part-jvm:$drillApiVersion")
    implementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    implementation(project(":test-framework:test-data"))
    implementation(kotlin("test-junit5"))
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=io.ktor.util.InternalAPI"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.ExperimentalStdlibApi"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=io.ktor.locations.KtorExperimentalLocationsAPI"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.time.ExperimentalTime"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlinx.coroutines.ObsoleteCoroutinesApi"
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            afterEvaluate {
                artifact(tasks.jar.get())
                artifact(sourcesJar.get())
            }
        }
    }
}
