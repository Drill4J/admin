plugins {
    base
}

val scriptUrl: String by extra

allprojects {
    apply(from = rootProject.uri("$scriptUrl/git-version.gradle.kts"))
}

subprojects {
    repositories {
        mavenLocal()
        apply(from = "$scriptUrl/maven-repo.gradle.kts")
        jcenter()
    }
}
