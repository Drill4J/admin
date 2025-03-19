import org.ajoberstar.grgit.Grgit


@Suppress("RemoveRedundantBackticks")
plugins {
    `distribution`
    kotlin("jvm").apply(false)
    kotlin("plugin.noarg").apply(false)
    kotlin("plugin.serialization").apply(false)
    id("org.ajoberstar.grgit")
    id("com.github.hierynomus.license").apply(false)
    id("com.github.johnrengelman.shadow").apply(false)
    id("com.epam.drill.integration.cicd") version "0.1.7"
}

group = "com.epam.drill.admin"

val kotlinVersion: String by extra
val kotlinxCoroutinesVersion: String by extra
val kotlinxSerializationVersion: String by extra

repositories {
    mavenLocal()
    mavenCentral()
}

buildscript {
    dependencies.classpath("org.apache.commons:commons-configuration2:2.9.0")
    dependencies.classpath("commons-beanutils:commons-beanutils:1.9.4")
}

if(version == Project.DEFAULT_VERSION) {
    val fromEnv: () -> String? = {
        System.getenv("GITHUB_REF")?.let { Regex("refs/tags/v(.*)").matchEntire(it)?.groupValues?.get(1) }
    }
    val fromGit: () -> String? = {
        val gitdir: (Any) -> Boolean = { projectDir.resolve(".git").isDirectory }
        takeIf(gitdir)?.let {
            val gitrepo = Grgit.open { dir = projectDir }
            val gittag = gitrepo.describe {
                tags = true
                longDescr = true
                match = listOf("v[0-9]*.[0-9]*.[0-9]*")
            }
            gittag?.trim()?.removePrefix("v")?.replace(Regex("-[0-9]+-g[0-9a-f]+$"), "")?.takeIf(String::any)
        }
    }
    version = fromEnv() ?: fromGit() ?: version
}

subprojects {
    val constraints = setOf(
        dependencies.constraints.create("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion"),
        dependencies.constraints.create("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion"),
        dependencies.constraints.create("org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion"),
        dependencies.constraints.create("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion"),
        dependencies.constraints.create("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$kotlinxCoroutinesVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinxCoroutinesVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinxSerializationVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$kotlinxSerializationVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$kotlinxSerializationVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinxSerializationVersion"),
    )
    configurations.all {
        dependencyConstraints += constraints
    }
//    apply(plugin = "com.epam.drill.integration.cicd")
//    drill {
//        apiUrl = "http://localhost:8090/api"
//        apiKey = "1_a84fa0d95719ab89730508ee63a5b215ece95e18872004cb5521134b84070029"
//        groupId = "drill"
//        appId = "drill-backend"
//        packagePrefixes = arrayOf("com/epam/drill")
//
//        enableTestRecommendations {}
//
//        enableTestAgent {
//            enabled = true
//            version = "0.23.5"
////            zipPath = "C:\\projects\\epam\\drill4j\\autotest-agent\\build\\distributions\\mingwX64-0.23.5-alpha.2.zip"
//        }
//        enableAppAgent {
//            enabled = true
//            version = "0.9.7"
////            zipPath = "C:\\projects\\epam\\drill4j\\java-agent\\build\\distributions\\java-agent\\mingwX64-0.9.6.zip"
//        }
//    }
}