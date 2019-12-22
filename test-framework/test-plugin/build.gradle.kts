plugins {
    distribution
    `maven-publish`
}

tasks {
    val pluginConfigJson = file("plugin_config.json")
    distributions {
        main {
            contents {
                from(getByPath(":test-framework:test-plugin:admin-part:jar"), getByPath(":test-framework:test-plugin:agent-part:jar"), pluginConfigJson)
                into("/")
            }
        }
    }

}