import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    jcenter()
}

val drillApiVersion: String by extra
val drillDsmVersion: String by extra

dependencies {
    implementation("com.epam.drill:plugin-api-admin:$drillApiVersion")
    implementation("com.epam.drill:common:$drillApiVersion")
    implementation("com.epam.drill.dsm:core:$drillDsmVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
tasks {
    jar {
        archiveFileName.set("admin-part.jar")
    }
}
