import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat

plugins {
    `maven-publish`
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
    id("com.github.hierynomus.license")
}

group = "com.epam.drill"
version = rootProject.version

val kotlinxSerializationVersion: String by parent!!.extra
val ktorVersion: String by parent!!.extra
val bcelVersion: String by parent!!.extra
val kodeinVersion: String by parent!!.extra
val microutilsLoggingVersion: String by parent!!.extra
val mapdbVersion: String by parent!!.extra

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("test-junit5"))
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-server-test-host:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinxSerializationVersion")
    implementation("org.kodein.di:kodein-di-framework-ktor-server-jvm:$kodeinVersion")
    implementation("org.apache.bcel:bcel:$bcelVersion")
    implementation("org.mapdb:mapdb:$mapdbVersion")
    implementation("io.github.microutils:kotlin-logging-jvm:$microutilsLoggingVersion")
    implementation("io.mockk:mockk:1.9.3")
    implementation("org.junit.jupiter:junit-jupiter:5.5.2")

    implementation(project(":common"))
    implementation(project(":plugin-api-admin"))
    implementation(project(":test-data"))
    implementation(project(":test-plugin-admin"))
    implementation(project(":test-plugin-agent"))

    api(project(":dsm"))
    api(project(":dsm-test-framework"))
    api(project(":admin-core"))
    api(project(":admin-auth"))
}

kotlin.sourceSets.all {
    languageSettings.optIn("kotlin.ExperimentalStdlibApi")
    languageSettings.optIn("kotlin.time.ExperimentalTime")
    languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
    languageSettings.optIn("kotlinx.coroutines.ObsoleteCoroutinesApi")
    languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
    languageSettings.optIn("io.ktor.locations.KtorExperimentalLocationsAPI")
    languageSettings.optIn("io.ktor.util.InternalAPI")
}

@Suppress("UNUSED_VARIABLE")
tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
    val sourcesJar by registering(Jar::class) {
        from(sourceSets.main.get().allSource)
        archiveClassifier.set("sources")
    }
}

publishing {
    publications.create<MavenPublication>("test-framework") {
        from(components["java"])
    }
}

@Suppress("UNUSED_VARIABLE")
license {
    headerURI = URI("https://raw.githubusercontent.com/Drill4J/drill4j/develop/COPYRIGHT")
    val licenseFormatSources by tasks.registering(LicenseFormat::class) {
        source = fileTree("$projectDir/src").also {
            include("**/*.kt", "**/*.java", "**/*.groovy")
        }
    }
    val licenseCheckSources by tasks.registering(LicenseCheck::class) {
        source = fileTree("$projectDir/src").also {
            include("**/*.kt", "**/*.java", "**/*.groovy")
        }
    }
}
