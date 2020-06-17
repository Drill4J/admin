package com.epam.drill.admin.admindata

import com.epam.drill.admin.methods.*
import com.epam.drill.common.*
import kotlinx.serialization.protobuf.*
import kotlin.test.*

class AgentBuildManagerTest {
    private val buildManager = AgentBuildManager("test")

    @Test
    fun `initBuildInfo with empty version`() {
        buildManager.initBuildInfo("")
        assertEquals(1, buildManager.builds.size)
    }

    @Test
    fun `initBuildInfo - two identical build versions`() {
        buildManager.initBuildInfo("0.1.0")
        buildManager.initBuildInfo("0.1.0")
        assertEquals(1, buildManager.builds.size)
    }

    @Test
    fun `initBuildInfo - two different build versions in a row`() {
        buildManager.initBuildInfo("0.1.0")
        buildManager.initBuildInfo("0.2.0")
        assertNotNull(buildManager["0.1.0"])
        assertNotNull(buildManager["0.2.0"])
        assertEquals("0.2.0", buildManager.lastBuild)
    }

    @Test
    fun `versions with and without whitespace don't match`() {
        buildManager.initBuildInfo("0.1.0")
        assertNull(buildManager[" 0.1.0 "])
        buildManager.initBuildInfo(" 0.1.0 ")
        assertEquals(2, buildManager.builds.size)
    }

    @Test
    fun `get non-existing version`() {
        buildManager.initBuildInfo("0.1.0")
        assertNull(buildManager["non-existing version"])
        assertNotNull(buildManager["0.1.0"])
    }

    @Test
    fun `addClass - before initBuildInfo`() {
        buildManager.addClass(String::class.java.dump())
        buildManager.initBuildInfo("0.1.0")
    }

    @Test
    fun `addClass - two identical classes in one build`() {
        val byteArray = String::class.java.dump()
        buildManager.initBuildInfo("0.1.0")
        buildManager.addClass(byteArray)
        buildManager.addClass(byteArray)
        buildManager.initClasses("0.1.0")
        assertEquals(1, buildManager["0.1.0"]?.classesBytes?.size)
    }

    @Test
    fun `initClasses - non-existing build version`() {
        assertFails { buildManager.initClasses("non-existing version") }
    }

    @Test
    fun `initClasses - empty added classes list`() {
        buildManager.initBuildInfo("0.1.0")
        buildManager.initClasses("0.1.0")
        assertEquals(emptyMap(), buildManager["0.1.0"]?.classesBytes)
    }

    @Test
    fun `dtoList - not initialized build info`() {
        val summaries = buildManager.dtoList()
        assertEquals(emptyList(), summaries)
    }

    @Test
    fun `dtoList - build without classes`() {
        buildManager.initBuildInfo("0.1.0")
        val summaries = buildManager.dtoList()
        assertNotEquals(emptyList(), summaries)
        assertNotNull(summaries.find { it.buildVersion == "0.1.0" })
    }

    private fun Class<*>.dump(): ByteArray = ProtoBuf.dump(
        ByteClass.serializer(),
        ByteClass(name.replace(".", "/"), readBytes())
    )
}
