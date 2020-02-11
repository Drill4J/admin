import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
    `kotlinx-serialization`
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    jcenter()
}


dependencies {
    implementation(kotlin("stdlib"))
    implementation(drill("drill-admin-part-jvm", drillApiVersion))
    implementation(drill("common-jvm", drillApiVersion))
    implementation("com.epam.drill:kodux-jvm:$koduxVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
tasks {
    val jar by existing(Jar::class) {
        archiveFileName.set("admin-part.jar")
    }
}
