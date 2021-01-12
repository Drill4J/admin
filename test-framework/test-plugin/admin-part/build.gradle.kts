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
val koduxVersion: String by extra

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.epam.drill:drill-admin-part-jvm:$drillApiVersion")
    implementation("com.epam.drill:common-jvm:$drillApiVersion")
    implementation("com.epam.drill:kodux-jvm:$koduxVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
tasks {
    jar {
        archiveFileName.set("admin-part.jar")
    }
}
