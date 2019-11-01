import com.google.gson.*
import com.palantir.gradle.gitversion.*
import groovy.lang.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.Constants
import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.*

val Project.versionDetails: VersionDetails
    get() {
        val versionDetails: Closure<VersionDetails> by extra
        return versionDetails()
    }

private fun Project.calculateProjectVersion() = object {
    val tagVersionRegex = Regex("refs/tags/v(\\d+)\\.(\\d+)\\.(\\d+)")

    override fun toString(): String {
        val resolve = this@calculateProjectVersion.rootProject.rootDir.resolve(".git")
        val git = Git.wrap(FileRepository(resolve))
        val lastTag = git.tagList().call().last { tagVersionRegex.matches(it.name) }
        val (_, major, minor, patch) = tagVersionRegex.matchEntire(lastTag.name)!!.groupValues
        val commitDistance = git.log()
            .addRange(lastTag.objectId, git.repository.findRef(Constants.HEAD).objectId).call().count()

        return when (commitDistance) {
            0 -> "$major.$minor.$patch"
            else -> "$major.${minor.toInt().inc()}.$patch-SNAPSHOT"
        }
    }
}

data class VersionInfo(
    val version: String,
    val lastTag: String,
    val commitDistance: Int,
    val gitHash: String
)

fun Project.setupVersion() {
    apply<GitVersionPlugin>()

    version = calculateProjectVersion()

    tasks {
        val generateVersionJson by registering {
            group = "versioning"
            val versionFile = buildDir.resolve("version.json")
            inputs.dir(this@setupVersion.rootProject.rootDir.resolve(".git"))
            outputs.file(versionFile)
            doLast {
                val versionInfo = VersionInfo(
                    version = "${project.version}",
                    lastTag = versionDetails.lastTag,
                    commitDistance = versionDetails.commitDistance,
                    gitHash = versionDetails.gitHash
                )
                versionFile.writeText(Gson().toJson(versionInfo))
            }
        }

        withType(ProcessResources::class) {
            from(generateVersionJson)
        }
    }
}
