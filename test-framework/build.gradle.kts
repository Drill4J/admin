import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

val drillApiVersion: String by extra
val drillLogger: String by extra
val serializationVersion: String by extra
val collectionImmutableVersion: String by extra
val ktorVersion: String by extra
val kodeinVersion: String by extra
val swaggerVersion: String by extra
val koduxVersion: String by extra
val xodusVersion: String by extra
val zstdJniVersion: String by extra

val junitVersion: String by extra
val mockkVersion: String by extra

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-server-test-host:$ktorVersion")
    implementation("com.epam.drill:drill-admin-part-jvm:$drillApiVersion")
    implementation("com.epam.drill:common-jvm:$drillApiVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serializationVersion")
    implementation("com.epam.drill:kodux-jvm:$koduxVersion")
    implementation("org.jetbrains.xodus:xodus-entity-store:1.3.91")
    implementation("org.kodein.di:kodein-di-generic-jvm:$kodeinVersion")
    implementation("io.mockk:mockk:$mockkVersion")
    api(project(":core"))
    implementation("org.apache.bcel:bcel:6.3.1")
    implementation("com.epam.drill:drill-agent-part-jvm:$drillApiVersion")
    implementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    implementation(project(":test-framework:test-data"))
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.util.InternalAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.ExperimentalStdlibApi"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.locations.KtorExperimentalLocationsAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.util.KtorExperimentalAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.time.ExperimentalTime"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.ObsoleteCoroutinesApi"
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}

publishing {
    repositories {
        maven {
            url = uri("http://oss.jfrog.org/oss-release-local")
            credentials {
                username =
                    if (project.hasProperty("bintrayUser"))
                        project.property("bintrayUser").toString()
                    else System.getenv("BINTRAY_USER")
                password =
                    if (project.hasProperty("bintrayApiKey"))
                        project.property("bintrayApiKey").toString()
                    else System.getenv("BINTRAY_API_KEY")
            }
        }
    }

    publications {
        create<MavenPublication>(project.name) {
            afterEvaluate {
                artifact(tasks.jar.get())
                artifact(sourcesJar.get())
            }
        }
    }
}
