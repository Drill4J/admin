import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    `kotlin-platform-jvm`
    `kotlinx-serialization`
    `maven-publish`
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(ktor("auth"))
    implementation(ktor("auth-jwt"))
    implementation(ktor("server-netty"))
    implementation(ktor("locations"))
    implementation(ktor("server-core"))
    implementation(ktor("websockets"))
    implementation(ktor("serialization"))
    implementation(drill("drill-admin-part-jvm", drillCommonVersion))
    implementation(drill("common-jvm", drillAdminPartVersion))
    implementation(ktor("server-test-host"))
    implementation("com.epam.drill:kodux-jvm:$koduxVersion")
    implementation("org.jetbrains.xodus:xodus-entity-store:1.3.91")
    implementation("org.kodein.di:kodein-di-generic-jvm:6.2.0")
    implementation("io.mockk:mockk:1.9.3")
    api(project(":core"))
    implementation("org.apache.bcel:bcel:$bcelVersion")
    implementation(drill("drill-agent-part-jvm", drillAdminPartVersion))
    implementation("org.junit.jupiter:junit-jupiter:5.5.2")
    implementation(project(":test-framework:test-data"))
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.util.InternalAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.locations.KtorExperimentalLocationsAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.util.KtorExperimentalAPI"
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
