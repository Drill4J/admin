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
    implementation("com.epam.drill:drill-agent-part:$drillApiVersion")
    implementation("com.epam.drill:common:$drillApiVersion")
}

tasks {
    val jar by existing(Jar::class){
        archiveFileName.set("agent-part.jar")
    }
}
