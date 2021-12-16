/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.kodein

import io.ktor.application.*
import org.kodein.di.*


fun AppBuilder(handler: AppBuilder.() -> Unit) = AppBuilder().apply { handler(this) }

class AppBuilder {
    val kodeinModules = mutableSetOf<KodeinConf.() -> DI.Module>()

    fun Application.withInstallation(installHandler: Application.() -> Unit): AppBuilder {
        installHandler(this)
        return this@AppBuilder
    }

    fun withKModule(kh: KodeinConf.() -> DI.Module): AppBuilder {
        kodeinModules.add(kh)
        return this@AppBuilder
    }


}

fun Application.kodeinApplication(
    appConfig: AppBuilder,
    kodeinMapper: DI.MainBuilder.(Application) -> Unit = {},
): DI {
    val app = this
    return DI {
        bind<Application>() with singleton { app }
        appConfig.kodeinModules.forEach { import(kodeinConfig(this) { it(this) }, allowOverride = true) }
        appConfig.kodeinModules.clear()
        kodeinMapper(this, app)
    }
}


fun Application.kodeinConfig(mainBuilder: DI.MainBuilder, hand: KodeinConf.(DI.MainBuilder) -> DI.Module) =
    KodeinConf(this, mainBuilder).run { hand(this, mainBuilder) }


class KodeinConf(val app: Application, val mainBuilder: DI.MainBuilder) {
    operator fun invoke(hand: KodeinConf.(DI.MainBuilder) -> Unit) {
        hand(this, mainBuilder)
    }

    fun kodeinModule(name: String, block: DI.Builder.(Application) -> Unit) =
        DI.Module(name = name, allowSilentOverride = true) { block(this, app) }
}
