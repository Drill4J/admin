import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.github.hierynomus.license")
    kotlin("plugin.serialization")
}

group = "com.epam.drill.admin"
version = rootProject.version

val kotlinVersion: String by parent!!.extra
val microutilsLoggingVersion: String by parent!!.extra
val ktorVersion: String by parent!!.extra
val kodeinVersion: String by parent!!.extra
val kotlinxSerializationVersion: String by parent!!.extra
val kotlinxDatetimeVersion: String by parent!!.extra
val mockitoKotlinVersion: String by parent!!.extra
val exposedVersion: String by parent!!.extra
val flywaydbVersion: String by parent!!.extra
val testContainersVersion: String by parent!!.extra
val postgresSqlVersion: String by parent!!.extra
val zaxxerHikaricpVersion: String by parent!!.extra
val quartzVersion: String by parent!!.extra

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin.sourceSets {
    all {
        languageSettings.optIn("kotlin.Experimental")
        languageSettings.optIn("kotlin.ExperimentalStdlibApi")
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("io.ktor.util.InternalAPI")
        languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        languageSettings.optIn("kotlinx.serialization.InternalSerializationApi")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":admin-common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-resources:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-test-host:$ktorVersion")
    implementation("io.ktor:ktor-client-mock:$ktorVersion")
    implementation("io.ktor:ktor-client-resources:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("org.kodein.di:kodein-di-framework-ktor-server-jvm:$kodeinVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    implementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
    implementation("org.testcontainers:testcontainers:$testContainersVersion")
    implementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    implementation("org.testcontainers:postgresql:$testContainersVersion")
    implementation("com.zaxxer:HikariCP:$zaxxerHikaricpVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.2")
    implementation("org.postgresql:postgresql:$postgresSqlVersion")
}

tasks {
    test {
        useJUnitPlatform()
    }
}

license {
    headerURI = URI("https://raw.githubusercontent.com/Drill4J/drill4j/develop/COPYRIGHT")
    include("**/*.kt")
}
