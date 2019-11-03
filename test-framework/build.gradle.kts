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
    implementation("com.epam.drill:kodux-jvm:0.1.1")
    implementation("org.kodein.di:kodein-di-generic-jvm:6.2.0")
    api(project(":admin:core"))
    implementation("org.apache.bcel:bcel:$bcelVersion")
}
tasks {

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.util.InternalAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.locations.KtorExperimentalLocationsAPI"
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
        }
    }
}

@Suppress("unused")
fun DependencyHandler.ktor(module: String, version: String? = ktorVersion): Any =
    "io.ktor:ktor-$module${version?.let { ":$version" } ?: ""}"

@Suppress("unused")
fun DependencyHandler.drill(module: String, version: Any? = project.version): Any =
    "com.epam.drill:$module${version?.let { ":$version" } ?: ""}"
