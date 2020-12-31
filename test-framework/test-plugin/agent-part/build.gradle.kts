plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
}

val drillApiVersion: String by extra
val serializationVersion: String by extra

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.epam.drill:drill-agent-part-jvm:$drillApiVersion")
    implementation("com.epam.drill:common-jvm:$drillApiVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationVersion")
}

tasks {
    val jar by existing(Jar::class){
        archiveFileName.set("agent-part.jar")
    }
}
