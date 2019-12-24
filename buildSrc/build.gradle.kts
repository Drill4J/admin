plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    maven(url = "https://dl.bintray.com/kotlin/kotlinx/")
    maven(url = "http://oss.jfrog.org/oss-release-local")
    jcenter()
}

val kotlinVersion = "1.3.60"
dependencies {
    implementation(kotlin("gradle-plugin", kotlinVersion))
    implementation(kotlin("stdlib-jdk8", kotlinVersion))
    implementation(kotlin("serialization", kotlinVersion))
    implementation(kotlin("reflect", kotlinVersion))
    implementation("com.epam.drill:gradle-plugin:0.6.0")
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}