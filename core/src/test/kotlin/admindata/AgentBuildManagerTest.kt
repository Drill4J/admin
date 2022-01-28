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
package com.epam.drill.admin.admindata

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.common.serialization.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.protobuf.*
import kotlin.test.*

class AgentBuildManagerTest {
    private val buildManager = AgentBuildManager("test")

    @Test
    fun `initBuildInfo with empty version`() {
        buildManager.init("")
        assertEquals(1, buildManager.agentBuilds.size)
    }

    @Test
    fun `initBuildInfo - two identical build versions`() {
        buildManager.init("0.1.0")
        buildManager.init("0.1.0")
        assertEquals(1, buildManager.agentBuilds.size)
    }

    @Test
    fun `initBuildInfo - two different build versions in a row`() {
        buildManager.init("0.1.0")
        buildManager.init("0.2.0")
        assertNotNull(buildManager["0.1.0"])
        assertNotNull(buildManager["0.2.0"])
        assertEquals(2, buildManager.agentBuilds.size)
    }

    @Test
    fun `versions with and without whitespace don't match`() {
        buildManager.init("0.1.0")
        assertNull(buildManager[" 0.1.0 "])
        buildManager.init(" 0.1.0 ")
        assertEquals(2, buildManager.agentBuilds.size)
    }

    @Test
    fun `get non-existing version`() {
        buildManager.init("0.1.0")
        assertNull(buildManager["non-existing version"])
        assertNotNull(buildManager["0.1.0"])
    }

    @Test
    fun `addClass - before initBuildInfo`() {
        buildManager.addClass(String::class.java.dump())
        buildManager.init("0.1.0")
    }

    @Test
    fun `addClass - two identical classes in one build`() {
        val byteArray = String::class.java.dump()
        buildManager.init("0.1.0")
        buildManager.addClass(byteArray)
        buildManager.addClass(byteArray)
        assertEquals(2, buildManager.collectClasses().size)
    }

    @Test
    fun `initClasses - empty added classes list`() {
        buildManager.init("0.1.0")
        assertEquals(0, buildManager.collectClasses().size)
    }

    @Test
    fun `dtoList - build without classes`() {
        buildManager.init("0.1.0")
        val builds = buildManager.agentBuilds
        assertNotEquals(persistentListOf(), builds)
        assertNotNull("0.1.0", builds.first().info.version)
    }

    private fun Class<*>.dump(): ByteArray = ProtoBuf.dump(
        ByteClass.serializer(),
        ByteClass(name, readBytes())
    )
}

private fun Class<*>.readBytes(): ByteArray = run {
    getResourceAsStream("/${canonicalName.replace('.', '/')}.class")
}.readBytes()
