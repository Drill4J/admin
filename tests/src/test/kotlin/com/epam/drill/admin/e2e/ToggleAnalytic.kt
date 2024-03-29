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
package com.epam.drill.admin.e2e

import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.version.*
import com.epam.drill.admin.waitUntil
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlin.test.Test

class ToggleAnalytic : E2ETest() {

    @Test
    fun `Toggle Analytic Test`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap("agentId")) { glob, _, _ ->
                waitUntil { glob.getAnalytic()?.isAnalyticsDisabled shouldBe true }
                toggleAnalytic(AnalyticsToggleDto.serializer() stringify AnalyticsToggleDto(disable = false)) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                waitUntil { glob.getAnalytic()?.isAnalyticsDisabled shouldBe false }
            }
        }
    }


}
