package com.epam.drill.admin.auth.module

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.routing.*
import org.kodein.di.DI

interface LazyConfiguration {
    fun withDI(di: DI.MainBuilder.() -> Unit): LazyConfiguration
    fun withAuthentication(authentication: Authentication.Configuration.() -> Unit): LazyConfiguration
    fun withFeatures(features: Application.() -> Unit): LazyConfiguration
    fun withRouting(routing: Routing.() -> Unit): LazyConfiguration
}

class LazyConfigurationContainer: LazyConfiguration {
    private val diConfigurations: MutableList<DI.MainBuilder.() -> Unit> = ArrayList()
    private val authConfigurations: MutableList<Authentication.Configuration.() -> Unit> = ArrayList()
    private val featureConfigurations: MutableList<Application.() -> Unit> = ArrayList()
    private val routingConfigurations: MutableList<Routing.() -> Unit> = ArrayList()

    override fun withDI(di: DI.MainBuilder.() -> Unit) = this.apply {
        diConfigurations.add(di)
    }

    override fun withAuthentication(authentication: Authentication.Configuration.() -> Unit) = this.apply {
        authConfigurations.add(authentication)
    }

    override fun withFeatures(features: Application.() -> Unit) = this.apply {
        featureConfigurations.add(features)
    }

    override fun withRouting(routing: Routing.() -> Unit) = this.apply {
        routingConfigurations.add(routing)
    }

    fun configureDI(context: DI.MainBuilder) {
        diConfigurations.forEach { with(context, it) }
    }

    fun configureAuthentication(context: Authentication.Configuration) {
        authConfigurations.forEach { with(context, it) }
    }

    fun configureFeature(context: Application) {
        featureConfigurations.forEach { with(context, it) }
    }

    fun configureRouting(context: Routing) {
        routingConfigurations.forEach { with(context, it) }
    }
}
