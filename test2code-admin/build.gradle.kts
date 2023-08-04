import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.noarg")
    id("kotlinx-atomicfu")
    id("com.github.johnrengelman.shadow")
    id("com.github.hierynomus.license")
}

group = "com.epam.drill.plugins.test2code"
version = rootProject.version

val kotlinxCoroutinesVersion: String by parent!!.extra
val kotlinxSerializationVersion: String by parent!!.extra
val kotlinxCollectionsVersion: String by parent!!.extra
val bcelVersion: String by parent!!.extra
val jacocoVersion: String by parent!!.extra
val atomicfuVersion: String by parent!!.extra
val lubenZstdVersion: String by parent!!.extra
val microutilsLoggingVersion: String by parent!!.extra

repositories {
    mavenLocal()
    mavenCentral()
}

@Suppress("HasPlatformType")
val jarDependencies by configurations.creating {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
}
configurations.implementation.get().extendsFrom(jarDependencies)

dependencies {
    jarDependencies(project(":test2code-api"))
    jarDependencies(project(":test2code-common"))

    compileOnly("org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")
    compileOnly("com.github.luben:zstd-jni:$lubenZstdVersion")

    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:$kotlinxCollectionsVersion")
    implementation("io.github.microutils:kotlin-logging-jvm:$microutilsLoggingVersion")
    implementation(project(":plugin-api-admin"))

    api(project(":dsm"))
    api(project(":dsm-annotations")) { isTransitive = false }
    api(project(":admin-analytics"))

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("ch.qos.logback:logback-classic:1.2.3")
    testImplementation(project(":dsm-test-framework"))
}

kotlin.sourceSets.all {
    languageSettings.optIn("kotlin.Experimental")
    languageSettings.optIn("kotlin.time.ExperimentalTime")
    languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
    languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
}

tasks {
    test {
        useJUnitPlatform()
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
    shadowJar {
        isZip64 = true
        configurations = listOf(jarDependencies)
        archiveFileName.set("admin-part.jar")
        destinationDirectory.set(file("$buildDir/shadowLibs"))
        dependencies {
            exclude("/META-INF/**", "/*.class", "/*.html")
        }
        relocate("org.jacoco", "${project.group}.shadow.org.jacoco")
        relocate("org.objectweb", "${project.group}.shadow.org.objectweb")
    }
}

noArg {
    annotation("kotlinx.serialization.Serializable")
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
