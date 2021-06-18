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
package com.epam.drill.admin.download

import com.epam.drill.admin.plugins.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import java.io.*
import kotlin.test.*
import kotlin.test.Test

@Disabled
class PluginDownloadTest {

    private lateinit var pluginStorageDir: File

    private val unknownPlugin = "unknown plugin"

    @BeforeTest
    fun before() {
        pluginStorageDir = File("build/tmp/test/plugin/storage/").also {
            it.mkdirs()
        }
    }

    @AfterTest
    fun after() {
        pluginStorageDir.deleteRecursively()
    }

    @Test
    fun `should successfully download t2c plugin`() = runBlocking {
        val loaderService = ArtifactoryPluginLoader(Artifactory.GITHUB, pluginStorageDir, false)
        loaderService.loadPlugins(defaultPlugins)
        assert(pluginStorageDir.listFiles()?.size == 1)
    }

    @Test
    fun `should be no exceptions when downloading the unknown plugin`() = runBlocking {
        val loaderService = ArtifactoryPluginLoader(Artifactory.GITHUB, pluginStorageDir, false)
        loaderService.loadPlugins(listOf(unknownPlugin))
        assert(pluginStorageDir.listFiles()?.size == 0)
    }

    @Test
    fun `should successfully download only available plugins`() = runBlocking {
        val loaderService = ArtifactoryPluginLoader(Artifactory.GITHUB, pluginStorageDir, false)
        loaderService.loadPlugins(defaultPlugins + unknownPlugin)
        assert(pluginStorageDir.listFiles()?.size == 1)
    }

}
