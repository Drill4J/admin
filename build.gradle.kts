plugins {
    base
    id("com.epam.drill.version.plugin")
}

subprojects {
    apply(plugin = "com.epam.drill.version.plugin")

    repositories {
        mavenLocal()
        maven(url = "https://dl.bintray.com/kotlin/kotlinx/")
        maven(url = "http://oss.jfrog.org/oss-release-local")
        jcenter()
    }
}
