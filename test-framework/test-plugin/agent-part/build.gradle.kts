plugins {
    kotlin("jvm")
    `kotlinx-serialization`
}

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(drill("drill-agent-part-jvm", drillAgentPartVersion))
    implementation(drill("common-jvm", drillCommonVersion))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
}

tasks {
    val jar by existing(Jar::class){
        archiveFileName.set("agent-part.jar")
    }
}