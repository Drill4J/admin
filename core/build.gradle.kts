import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
    id("kotlinx-serialization")
    id("com.google.cloud.tools.jib") version "1.6.1"
    application
    `maven-publish`
    idea
    id("com.github.johnrengelman.shadow") version "5.1.0"
}

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
    maven(url = "https://dl.bintray.com/kodein-framework/Kodein-DI/")
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
}

val appMainClassName by extra("io.ktor.server.netty.EngineMain")

val appJvmArgs = listOf(
    "-server",
    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006",
    "-Djava.awt.headless=true",
    "-Xms128m",
    "-Xmx2g",
    "-XX:+UseG1GC",
    "-XX:MaxGCPauseMillis=100"
)


application {
    mainClassName = appMainClassName
    applicationDefaultJvmArgs = appJvmArgs
}
val testData by configurations.creating {}
val integrationTestImplementation by configurations.creating {
    extendsFrom(configurations["testCompile"])
}
val integrationTestRuntime by configurations.creating {
    extendsFrom(configurations["testRuntime"])
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(ktor("auth"))
    implementation(ktor("auth-jwt"))
    implementation(ktor("server-netty"))
    implementation(ktor("locations"))
    implementation(ktor("server-core"))
    implementation(ktor("websockets"))
    implementation(ktor("client-cio"))
    implementation(ktor("serialization"))
    implementation(project(":plugin-api:drill-admin-part"))
    implementation(project(":common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    implementation("org.kodein.di:kodein-di-generic-jvm:6.2.0")
    implementation("io.github.microutils:kotlin-logging:1.6.24")
    implementation("org.slf4j:slf4j-simple:1.7.26")
    implementation("ch.qos.logback:logback-classic:1.0.13")
    implementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    implementation("io.vavr:vavr-kotlin:$vavrVersion")
    implementation("org.apache.bcel:bcel:$bcelVersion")
    implementation("com.epam.drill:kodux-jvm:$koduxVersion")
    implementation("org.jetbrains.xodus:xodus-entity-store:1.3.91")
    implementation("com.epam.drill:ktor-swagger:$swaggerVersion")
    testImplementation(kotlin("test-junit"))
    testImplementation(project(":admin:test-framework"))
    testImplementation("io.mockk:mockk:1.9.3")
    integrationTestImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    integrationTestImplementation(ktor("server-test-host"))
    integrationTestImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
    add("testData", project(":admin:test-framework:test-data"))

}

jib {
    from {
        image = "gcr.io/distroless/java:8"
    }
    to {
        image = "drill4j/admin"
        tags = setOf("${project.version}")
    }
    container {
        ports = listOf("8090", "5006")
        mainClass = appMainClassName

        jvmFlags = appJvmArgs
    }
}
val testIngerationModuleName = "test-integration"

sourceSets {
    create(testIngerationModuleName) {
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDir("src/$testIngerationModuleName/kotlin")
            resources.srcDir("src/$testIngerationModuleName/resources")
            compileClasspath += sourceSets["main"].output + integrationTestImplementation + configurations["testRuntimeClasspath"]
            runtimeClasspath += output + compileClasspath + sourceSets["test"].runtimeClasspath + integrationTestRuntime
        }
    }

    (1..2).forEach {
        create("build$it") {
            java.srcDir("src/test-data/build$it/java")
            dependencies {
                implementation(project(":admin:test-framework:test-data"))
            }
            compileClasspath += testData
            runtimeClasspath += output + compileClasspath
        }
        tasks["testIntegrationClasses"].dependsOn("build${it}Classes")
    }
}
idea {
    module {
        testSourceDirs =
            (sourceSets[testIngerationModuleName].withConvention(KotlinSourceSet::class) { kotlin.srcDirs })
        testResourceDirs = (sourceSets[testIngerationModuleName].resources.srcDirs)
        scopes["TEST"]?.get("plus")?.add(integrationTestImplementation)
    }
}
val testPluginProject = project(":admin:test-framework:test-plugin")
val prepareDist = tasks.register<Copy>("prepareDist") {
    from(testPluginProject.tasks["distZip"])
    into(file("distr").resolve("adminStorage"))
}
task<Test>("integrationTest") {
    dependsOn(prepareDist)
    systemProperty("plugin.config.path", testPluginProject.projectDir.resolve("plugin_config.json"))
    useJUnitPlatform()
    description = "Runs the integration tests"
    group = "verification"
    testClassesDirs = sourceSets[testIngerationModuleName].output.classesDirs
    classpath = sourceSets[testIngerationModuleName].runtimeClasspath
    mustRunAfter(tasks["test"])
}

tasks.named("check") {
    dependsOn("integrationTest")

}

tasks {
    clean {
        delete("./work", "./distr", "./../distr")
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.locations.KtorExperimentalLocationsAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.util.KtorExperimentalAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.util.InternalAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.time.ExperimentalTime"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.serialization.ImplicitReflectionSerializer"
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
        create<MavenPublication>("adminFull") {
            artifact(tasks["shadowJar"])
            artifactId = "admin-core"
        }
        create<MavenPublication>("admin") {
            artifact(tasks["jar"])
            artifactId = "admin-core"
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

fun DependencyHandler.integrationTestImplementation(dependencyNotation: Any): Dependency? =
    add("integrationTestImplementation", dependencyNotation)