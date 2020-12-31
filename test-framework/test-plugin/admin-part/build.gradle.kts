import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    jcenter()
}

val drillApiVersion: String by extra
val koduxVersion: String by extra
val serializationVersion: String by extra

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.epam.drill:drill-admin-part-jvm:$drillApiVersion")
    implementation("com.epam.drill:common-jvm:$drillApiVersion")
    implementation("com.epam.drill:kodux-jvm:$koduxVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
tasks {
    jar {
        archiveFileName.set("admin-part.jar")
    }
}
