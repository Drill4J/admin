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
    implementation(kotlin("stdlib"))
    implementation("com.epam.drill:drill-agent-part-jvm:$drillApiVersion")
    implementation("com.epam.drill:common-jvm:$drillApiVersion")
}

tasks {
    val jar by existing(Jar::class){
        archiveFileName.set("agent-part.jar")
    }
}
