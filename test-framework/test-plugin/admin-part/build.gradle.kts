import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    jcenter()
}


dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":plugin-api:drill-admin-part"))
    implementation(project(":common"))
    implementation("com.epam.drill:kodux-jvm:$koduxVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
tasks {
    val jar by existing(Jar::class) {
        archiveFileName.set("admin-part.jar")
    }
}