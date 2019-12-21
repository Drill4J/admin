import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    `kotlin-platform-jvm`
    `kotlinx-serialization`
    application
    `maven-publish`
    id("com.google.cloud.tools.jib") version "1.7.0"
    id("com.github.johnrengelman.shadow") version "5.1.0"
}

val `test-integration`: SourceSet by sourceSets.creating

val testData: Configuration by configurations.creating

val testBuildSourceSets: List<SourceSet> = listOf("build1", "build2").map { testBuildName ->
    sourceSets.create(testBuildName) {
        java.setSrcDirs("src/test-data/$testBuildName/java".let(::listOf))
        compileClasspath += testData
    }
}

val intTestImplementationCfg by configurations.named(`test-integration`.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get(), testData)
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
    implementation(ktor("client-cio"))
    implementation(ktor("serialization"))
    implementation(project(":plugin-api:drill-admin-part"))
    implementation(project(":common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3")
    implementation("org.kodein.di:kodein-di-generic-jvm:6.2.0")
    implementation("io.github.microutils:kotlin-logging:1.7.8")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    implementation("io.vavr:vavr-kotlin:$vavrVersion")
    implementation("org.apache.bcel:bcel:$bcelVersion")
    implementation("com.epam.drill:kodux-jvm:$koduxVersion")
    implementation("org.jetbrains.xodus:xodus-entity-store:1.3.91")
    implementation("com.epam.drill:ktor-swagger:$swaggerVersion")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation(project(":admin:test-framework"))
    testImplementation("io.mockk:mockk:1.9.3")
    intTestImplementationCfg("io.kotlintest:kotlintest-runner-junit5:3.4.2")
    intTestImplementationCfg(ktor("server-test-host"))
    testData(project(":admin:test-framework:test-data"))
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

val jibExtraDirs = "$buildDir/jib-extra-dirs"

jib {
    from {
        image = "gcr.io/distroless/java:8-debug"
    }
    to {
        image = "drill4j/admin"
        tags = setOf("${project.version}")
    }
    container {
        ports = listOf("8090", "5006")
        mainClass = appMainClassName
        volumes = listOf("/work", "/distr")

        jvmFlags = appJvmArgs
    }
    extraDirectories {
        setPaths(jibExtraDirs)
        permissions = mapOf("/work" to "775", "/distr" to "775")
    }
}

val testPluginProject = project(":admin:test-framework:test-plugin")

tasks {
    clean {
        delete("work", "distr")
    }

    val prepareDist by registering(Copy::class) {
        from(testPluginProject.tasks["distZip"])
        into(file("distr").resolve("adminStorage"))
    }

    withType<Test> {
        useJUnitPlatform()
    }

    named(`test-integration`.classesTaskName) {
        testBuildSourceSets.forEach { srcSet ->
            dependsOn(srcSet.classesTaskName)
        }
    }
    
    val integrationTest by registering(Test::class) {
        dependsOn(prepareDist)
        mustRunAfter(test)
        systemProperty("plugin.config.path", testPluginProject.projectDir.resolve("plugin_config.json"))
        description = "Runs the integration tests"
        group = "verification"
        testClassesDirs = `test-integration`.output.classesDirs
        classpath = `test-integration`.runtimeClasspath
    }
    
    check {
        dependsOn(integrationTest)
    }

    val makeJibExtraDirs by registering(Copy::class) {
        group = "jib"
        outputs.upToDateWhen(Specs.satisfyNone())
        into(jibExtraDirs)
        from("src/main/jib")
        from("temporary.jks")
        doLast {
            listOf("work", "distr")
                .map(destinationDir::resolve)
                .forEach { mkdir(it) }
        }
    }

    withType<com.google.cloud.tools.jib.gradle.JibTask> {
        dependsOn(makeJibExtraDirs)
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
        create<MavenPublication>("adminFull") {
            artifact(tasks.shadowJar.get())
            artifactId = "admin-core"
        }
        create<MavenPublication>("admin") {
            artifact(tasks.jar.get())
            artifactId = "admin-core"
            artifact(sourcesJar.get())
        }
    }
}
