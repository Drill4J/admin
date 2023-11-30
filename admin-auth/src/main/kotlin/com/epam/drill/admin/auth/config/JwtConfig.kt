/**
 * Copyright 2020 - 2022 EPAM Systems
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
package com.epam.drill.admin.auth.config

import io.ktor.application.*
import io.ktor.config.*
import mu.KotlinLogging
import org.kodein.di.*
import javax.crypto.KeyGenerator
import kotlin.time.*

private val logger = KotlinLogging.logger {}

/**
 * A Ktor configuration for JWT based on the key "drill.auth.jwt".
 */
class JwtConfig(override val di: DI) : DIAware {
    private val app by instance<Application>()
    private val jwt: ApplicationConfig
        get() = app.environment.config
            .config("drill")
            .config("auth")
            .config("jwt")

    private val generatedSecret: String by lazy {
        logger.warn {
            "The generated secret key for the JWT is used. " +
                    "To set your secret key, use the DRILL_JWT_SECRET environment variable."
        }
        generateSecret()
    }

    /**
     * A secret for algorithm SHA512. Optional, a generated secret by default.
     */
    val secret: String
        get() = jwt.propertyOrNull("secret")?.getString() ?: generatedSecret

    /**
     * A JWT issuer. Optional, "Drill4J App" by default.
     */
    val issuer: String
        get() = jwt.propertyOrNull("issuer")?.getString() ?: "Drill4J App"

    /**
     * A lifetime of a JWT. Optional, 30 minutes by default.
     */
    val lifetime: Duration
        get() = jwt.propertyOrNull("lifetime")?.getDuration() ?: Duration.minutes(30)

    /**
     * An JWT audience. Optional, empty by default.
     */
    val audience: String?
        get() = jwt.propertyOrNull("audience")?.getString()
}

private fun ApplicationConfigValue.getDuration(): Duration {
    return Duration.parse(getString())
}


internal fun generateSecret() = KeyGenerator.getInstance("HmacSHA512").generateKey().encoded.contentToString()