rootProject.name = "admin"

include(":core")
include(":test-framework")
include(":test-framework:test-data")
include(":test-framework:test-plugin")
include(":test-framework:test-plugin:admin-part")
include(":test-framework:test-plugin:agent-part")

buildCache {
    local {
        directory = rootDir.resolve("build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}
