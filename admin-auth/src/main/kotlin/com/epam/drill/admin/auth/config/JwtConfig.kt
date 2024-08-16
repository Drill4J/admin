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

import io.ktor.server.config.*
import mu.KotlinLogging
import java.util.*
import javax.crypto.KeyGenerator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

/**
 * A JWT configuration.
 * @param config the Ktor configuration
 */
class JwtConfig(private val config: ApplicationConfig) {

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
        get() = config.propertyOrNull("secret")?.getString() ?: generatedSecret

    /**
     * A JWT issuer. Optional, "Drill4J App" by default.
     */
    val issuer: String
        get() = config.propertyOrNull("issuer")?.getString() ?: "Drill4J App"

    /**
     * A lifetime of a JWT. Optional, 60 minutes by default.
     */
    val lifetime: Duration
        get() = config.propertyOrNull("lifetime")?.getDuration() ?: 60.minutes

    /**
     * An JWT audience. Optional, empty by default.
     */
    val audience: String?
        get() = config.propertyOrNull("audience")?.getString()
}

private fun ApplicationConfigValue.getDuration(): Duration {
    return Duration.parse(getString())
}


internal fun generateSecret() = KeyGenerator.getInstance("HmacSHA512").generateKey().encoded
    .let { Base64.getEncoder().encodeToString(it) }