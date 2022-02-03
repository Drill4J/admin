plugins {
    kotlin("jvm")
    id("kotlinx-atomicfu")
    kotlin("plugin.serialization")
    `maven-publish`
}

val drillLogger: String by extra
val collectionImmutableVersion: String by extra
val ktorVersion: String by extra
val muLogger: String by extra

val junitVersion: String by extra

dependencies {
    compileOnly(kotlin("stdlib-jdk8"))
    api("io.ktor:ktor-server-core:$ktorVersion")
    compileOnly("io.ktor:ktor-client-cio:$ktorVersion")
    compileOnly("io.ktor:ktor-serialization:$ktorVersion")
    compileOnly("org.jetbrains.kotlinx:kotlinx-collections-immutable:$collectionImmutableVersion")
    //TODO remove logger - EPMDJ-9548
    compileOnly("io.github.microutils:kotlin-logging-jvm:$muLogger")

    testImplementation(kotlin("stdlib-jdk8"))
    testRuntimeOnly(project(":core"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = listOf(
                "-Xopt-in=io.ktor.util.InternalAPI",
                "-Xopt-in=kotlin.Experimental",
                "-Xopt-in=kotlin.ExperimentalStdlibApi",
                "-Xopt-in=kotlin.time.ExperimentalTime",
                "-Xopt-in=kotlinx.coroutines.DelicateCoroutinesApi",
                "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }

    test {
        useJUnitPlatform()
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}

publishing {
    publications {
        create<MavenPublication>("analytics") {
            artifact(tasks.jar.get())
            artifactId = "admin-analytics-core"
            artifact(sourcesJar.get())
        }
    }
}
