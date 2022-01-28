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
package com.epam.drill.admin.plugins

import kotlinx.coroutines.*
import java.io.*
import kotlin.test.*


class PluginLoadServiceTest {

    private lateinit var pluginStorageDir: File

    @BeforeTest
    fun before() {
        pluginStorageDir = File("build/tmp/test/plugin/load/storage/").also {
            it.mkdirs()
        }
    }

    @AfterTest
    fun after() {
        pluginStorageDir.deleteRecursively()
    }

    @Test
    fun `copy migration files from jar`() = runBlocking {
        val adminPartFile = File("src/test/resources", "admin-part-with-one-sql.jar")
        assertTrue { adminPartFile.isFile }
        val migrationDir = copyMigrationFilesFromJar(pluginStorageDir, "test-plugin", adminPartFile)
        assertEquals(1, migrationDir.listFiles()?.size)
    }

}
