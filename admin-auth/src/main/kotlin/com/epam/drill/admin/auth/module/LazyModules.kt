package com.epam.drill.admin.auth.module

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.routing.*
import io.ktor.util.*
import org.kodein.di.ktor.di

val LazyConfigurationContainerKey: AttributeKey<LazyConfigurationContainer> = AttributeKey("LazyModules")

class LazyModules private constructor() {

    class Configuration {
        var container: LazyConfigurationContainer = LazyConfigurationContainer()
        fun withContainer(container: LazyConfigurationContainer) {
            this.container = container
        }
    }

    companion object Feature : ApplicationFeature<Application, Configuration, LazyModules> {
        override val key: AttributeKey<LazyModules> = AttributeKey("[Global LazyModules Feature]")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): LazyModules {
            return LazyModules().apply {
                pipeline.attributes.put(LazyConfigurationContainerKey, Configuration().apply(configure).container)
            }
        }
    }
}

fun Application.lazyModule(features: Application.() -> Unit = {}): LazyConfiguration {
    return modules().apply { withFeatures(features) }
}

fun Application.initLazyModules() {
    modules().run {
        di {
            configureDI(this)
        }
        install(Authentication) {
            configureAuthentication(this)
        }
        configureFeature(this@initLazyModules)
        routing {
            configureRouting(this)
        }
    }
}

private fun Application.modules(): LazyConfigurationContainer {
    return attributes[LazyConfigurationContainerKey]
}
