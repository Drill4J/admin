import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat

plugins {
    kotlin("jvm")
    id("com.github.hierynomus.license")
    kotlin("plugin.serialization")
}

group = "com.epam.drill.admin.auth"
version = rootProject.version

val kotlinVersion: String by parent!!.extra
val microutilsLoggingVersion: String by parent!!.extra
val ktorVersion: String by parent!!.extra
val kodeinVersion: String by parent!!.extra
val kotlinxSerializationVersion: String by parent!!.extra
val mockitoKotlinVersion: String by parent!!.extra
val jbcryptVersion: String by parent!!.extra
val exposedVersion: String by parent!!.extra

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin.sourceSets {
    all {
        languageSettings.optIn("kotlin.Experimental")
        languageSettings.optIn("kotlin.ExperimentalStdlibApi")
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("io.ktor.locations.KtorExperimentalLocationsAPI")
        languageSettings.optIn("io.ktor.util.InternalAPI")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("io.github.microutils:kotlin-logging-jvm:$microutilsLoggingVersion")
    implementation("org.kodein.di:kodein-di-framework-ktor-server-jvm:$kodeinVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation("org.mindrot:jbcrypt:$jbcryptVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
}

tasks {
    test {
        useJUnitPlatform()
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
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
