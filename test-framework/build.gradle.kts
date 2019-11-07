import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
    id("kotlinx-serialization")
    `maven-publish`
}
repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
    maven(url = "https://dl.bintray.com/kodein-framework/Kodein-DI/")
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(ktor("auth"))
    implementation(ktor("auth-jwt"))
    implementation(ktor("server-netty"))
    implementation(ktor("locations"))
    implementation(ktor("server-core"))
    implementation(ktor("websockets"))
    implementation(project(":plugin-api:drill-admin-part"))
    implementation(project(":common"))
    implementation(ktor("server-test-host"))
    implementation("com.epam.drill:kodux-jvm:0.1.3")
    implementation("org.jetbrains.xodus:xodus-entity-store:1.3.91")
    implementation("org.kodein.di:kodein-di-generic-jvm:6.2.0")
    implementation("io.mockk:mockk:1.9.3")
    api(project(":admin:core"))
    implementation("org.apache.bcel:bcel:$bcelVersion")
    implementation(project(":plugin-api:drill-agent-part"))
    implementation("org.junit.jupiter:junit-jupiter:5.5.2")
    implementation(project(":admin:test-framework:test-data"))
}
tasks {

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.util.InternalAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.locations.KtorExperimentalLocationsAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.util.KtorExperimentalAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi"
    }
    register<Jar>("sourcesJar") {
        from(sourceSets.main.get().allSource)
        archiveClassifier.set("sources")
    }
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
            artifact(tasks["jar"])
            artifact(tasks["sourcesJar"])
        }
    }
}

@Suppress("unused")
fun DependencyHandler.ktor(module: String, version: String? = ktorVersion): Any =
    "io.ktor:ktor-$module${version?.let { ":$version" } ?: ""}"

@Suppress("unused")
fun DependencyHandler.drill(module: String, version: Any? = project.version): Any =
    "com.epam.drill:$module${version?.let { ":$version" } ?: ""}"
