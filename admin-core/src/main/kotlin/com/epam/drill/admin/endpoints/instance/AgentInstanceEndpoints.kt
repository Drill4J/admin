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
package com.epam.drill.admin.endpoints.instance

import com.epam.drill.admin.auth.config.withRole
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.config.drillConfig
import com.epam.drill.admin.writer.rawdata.route.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.response.*
import io.ktor.routing.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun Routing.agentInstanceRoutes() {

    intercept(ApplicationCallPipeline.Call) {
        call.response.header("drill-internal", "true")
        proceed()
    }

    val jsCoverageConverterAddress = application.drillConfig.config("test2code")
        .propertyOrNull("jsCoverageConverterAddress")
        ?.getString()
        ?.takeIf { it.isNotBlank() }
        ?: "http://localhost:8092" // TODO think of default

    route("/api") {
        authenticate("api-key") {
            withRole(Role.USER, Role.ADMIN) {
                putAgentConfig()
                postCoverage()
                postCLassMetadata()
                postClassMetadataComplete()
                postTestMetadata()
                postRawJavaScriptCoverage(jsCoverageConverterAddress)
            }
        }
    }

}
