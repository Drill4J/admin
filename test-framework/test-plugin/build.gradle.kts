plugins {
    distribution
    `maven-publish`
}

tasks {
    val pluginConfigJson = file("plugin_config.json")
    distributions {
        main {
            contents {
                from(getByPath(":admin:test-framework:test-plugin:admin-part:jar"), getByPath(":admin:test-framework:test-plugin:agent-part:jar"), pluginConfigJson)
                into("/")
            }
        }
    }

}