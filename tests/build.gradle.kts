import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.hierynomus.license")
}

group = "com.epam.drill"
version = rootProject.version

repositories {
    mavenLocal()
    mavenCentral()
}

val kotlinxCollectionsVersion: String by parent!!.extra
val kotlinxSerializationVersion: String by parent!!.extra
val ktorVersion: String by parent!!.extra
val kodeinVersion: String by parent!!.extra
val microutilsLoggingVersion: String by parent!!.extra
val testsSkipIntegrationTests: String by parent!!.extra

sourceSets {
    create("build1")
    create("build2")
}

@Suppress("HasPlatformType")
val testData by configurations.creating

configurations {
    testImplementation.get().extendsFrom(testData)
    getByName("build1Implementation").extendsFrom(testData)
    getByName("build2Implementation").extendsFrom(testData)
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    testImplementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:$kotlinxCollectionsVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    //    testImplementation("org.kodein.di:kodein-di-jvm:$kodeinVersion")
    testImplementation("org.kodein.di:kodein-di-framework-ktor-server-jvm:$kodeinVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-auth:$ktorVersion")
    testImplementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
    testImplementation("io.ktor:ktor-locations:$ktorVersion")
    testImplementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-websockets:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-serialization:$ktorVersion")
    testImplementation("io.github.microutils:kotlin-logging-jvm:$microutilsLoggingVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.2")
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation(kotlin("test-junit5"))

    testImplementation(project(":admin-core"))
    testImplementation(project(":admin-auth"))
    testImplementation(project(":common"))
    testImplementation(project(":ktor-swagger"))
    testImplementation(project(":plugin-api-admin"))
    testImplementation(project(":test-framework"))

    testData(project(":test-data"))
}

kotlin.sourceSets.all {
    languageSettings.optIn("kotlin.Experimental")
    languageSettings.optIn("kotlin.time.ExperimentalTime")
    languageSettings.optIn("io.ktor.locations.KtorExperimentalLocationsAPI")
}

tasks {
    val prepareDistr by registering(Sync::class) {
        from(project(":test-plugin").tasks["distZip"])
        into("distr/adminStorage")
    }
    val integrationTest by registering(Test::class) {
        description = "Runs integration tests"
        group = "verification"
        enabled = !testsSkipIntegrationTests.toBoolean()
        useJUnitPlatform()
        systemProperty("analytic.disable", true)
        dependsOn("build1Classes")
        dependsOn("build2Classes")
        dependsOn(prepareDistr)
        mustRunAfter(test)
    }
    check.get().dependsOn(integrationTest)
    clean {
        delete("distr")
        delete("work")
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
