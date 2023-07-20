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
package com.epam.drill.plugins.test2code.test.js

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.common.api.*

val jsAgentInfo = AgentInfo(
    id = "jsag",
    name = "jsag",
    description = "",
    buildVersion = "0.1.0",
    agentType = "NODEJS",
    agentVersion = ""
)

val ast = listOf(
    AstEntity(
        path = "foo/bar",
        name = "baz.js",
        methods = listOf(
            AstMethod(
                name = "foo",
                params = listOf("one", "two"),
                returnType = "number",
                probes = listOf(1, 2)
            ),
            AstMethod(
                name = "bar",
                params = listOf(),
                returnType = "void",
                probes = listOf(3)
            ),
            AstMethod(
                name = "baz",
                params = listOf(),
                returnType = "void",
                probes = listOf(4, 5)
            )

        )
    )
)

val probes = listOf(
    ExecClassData(
        className = "foo/bar/baz.js",
        testName = "default",
        probes = probesOf(true, true, false, true, false)
    )
)
val probes2 = listOf(
    ExecClassData(
        className = "foo/bar/baz.js",
        testName = "default",
        probes = probesOf(true, false, true, true, false)
    )
)

object IncorrectProbes {
    val overCount = listOf(
        ExecClassData(
            className = "foo/bar/baz.js",
            testName = "default",
            probes = probesOf(true, true, false, true, false, /*extra*/ false)
        )
    )

    val underCount = listOf(
        ExecClassData(
            className = "foo/bar/baz.js",
            testName = "default",
            probes = probesOf(true, true, false, true)
        )
    )

    val notExisting = listOf(
        ExecClassData(
            className = "foo/bar/not-existing",
            testName = "default",
            probes = probesOf(false, false)
        )
    )
}

