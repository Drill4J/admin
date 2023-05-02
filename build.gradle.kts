import org.jetbrains.kotlin.util.prefixIfNot
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Branch
import org.ajoberstar.grgit.operation.BranchListOp

plugins {
    kotlin("jvm").apply(false)
    kotlin("multiplatform").apply(false)
    kotlin("plugin.noarg").apply(false)
    kotlin("plugin.serialization").apply(false)
    id("kotlinx-atomicfu").apply(false)
    id("org.ajoberstar.grgit")
    id("com.github.hierynomus.license").apply(false)
    id("com.google.cloud.tools.jib").apply(false)
}

group = "com.epam.drill"

val kotlinVersion: String by extra
val kotlinxCollectionsVersion: String by extra
val kotlinxCoroutinesVersion: String by extra
val kotlinxSerializationVersion: String by extra

repositories {
    mavenLocal()
    mavenCentral()
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
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-collections-immutable:$kotlinxCollectionsVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:$kotlinxCollectionsVersion"),
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
}

@Suppress("UNUSED_VARIABLE")
tasks {
    val sharedLibsDir = file("$projectDir/lib-jvm-shared")
    val sharedLibsRef: String by extra
    val updateSharedLibs by registering {
        group = "other"
        doLast {
            val gitrepo = Grgit.open { dir = sharedLibsDir }
            val branches = gitrepo.branch.list { mode = BranchListOp.Mode.LOCAL }
            val branchToName: (Branch) -> String = { it.name }
            val branchIsCreate: (String) -> Boolean = { !branches.map(branchToName).contains(it) }
            gitrepo.fetch()
            gitrepo.checkout {
                branch = sharedLibsRef
                startPoint = sharedLibsRef.takeIf(branchIsCreate)?.prefixIfNot("origin/")
                createBranch = branchIsCreate(sharedLibsRef)
            }
            gitrepo.pull()
        }
    }
}
