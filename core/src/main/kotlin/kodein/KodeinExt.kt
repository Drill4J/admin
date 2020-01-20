package com.epam.drill.admin.kodein

import io.ktor.application.*
import org.kodein.di.*
import org.kodein.di.generic.*


fun AppBuilder(handler: AppBuilder.() -> Unit): AppBuilder {
    val appBuilder = AppBuilder()
    handler(appBuilder)
    return appBuilder
}

class AppBuilder {
    val kodeinModules = mutableSetOf<KodeinConf.() -> Kodein.Module>()

    fun Application.withInstallation(installHandler: Application.() -> Unit): AppBuilder {
        installHandler(this)
        return this@AppBuilder
    }

    fun withKModule(kh: KodeinConf.() -> Kodein.Module): AppBuilder {
        kodeinModules.add(kh)
        return this@AppBuilder
    }


}

fun Application.kodeinApplication(
    appConfig: AppBuilder,
    kodeinMapper: Kodein.MainBuilder.(Application) -> Unit = {}
): Kodein {
    val app = this
    return Kodein {
        bind<Application>() with singleton { app }
        appConfig.kodeinModules.forEach { import(kodeinConfig(this) { it(this) }, allowOverride = true) }
        appConfig.kodeinModules.clear()
        kodeinMapper(this, app)
    }
}


fun Application.kodeinConfig(mainBuilder: Kodein.MainBuilder, hand: KodeinConf.(Kodein.MainBuilder) -> Kodein.Module) =
    KodeinConf(this, mainBuilder).run { hand(this, mainBuilder) }


class KodeinConf(val app: Application, val mainBuilder: Kodein.MainBuilder) {
    operator fun invoke(hand: KodeinConf.(Kodein.MainBuilder) -> Unit) {
        hand(this, mainBuilder)
    }

    fun kodeinModule(name: String, block: Kodein.Builder.(Application) -> Unit) =
        Kodein.Module(name = name, allowSilentOverride = true) { block(this, app) }
}
