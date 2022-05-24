import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
    `maven-publish`
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
}

tasks {

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            artifact(tasks["jar"])
        }
    }
}
