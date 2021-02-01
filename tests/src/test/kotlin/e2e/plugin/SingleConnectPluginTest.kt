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
package com.epam.drill.admin.e2e.plugin

import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlin.test.*

class SingleConnectPluginTest : E2EPluginTest() {

    @Test
    fun `test e2e plugin API`() {
        createSimpleAppWithPlugin<PTestStream> {
            connectAgent<Build1>("myGroup") { _, _ ->
                val expectedContent = StatusMessageResponse.serializer() stringify StatusMessageResponse(
                    code = 200,
                    message = "act"
                )
                pluginAction("x") { status, content ->
                    status shouldBe HttpStatusCode.OK
                    content shouldBe expectedContent
                }.join()
                println("hi ag1")
            }.reconnect<Build2> { _, _ ->
                println("hi reconnected ag1")
            }
        }

    }


}
