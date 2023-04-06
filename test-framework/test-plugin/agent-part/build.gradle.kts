plugins {
    kotlin("jvm")
}

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
}

val drillApiVersion: String by extra

dependencies {
    implementation("com.epam.drill:plugin-api-agent:$drillApiVersion")
    implementation("com.epam.drill:common:$drillApiVersion")
}

tasks {
    val jar by existing(Jar::class){
        archiveFileName.set("agent-part.jar")
    }
}
