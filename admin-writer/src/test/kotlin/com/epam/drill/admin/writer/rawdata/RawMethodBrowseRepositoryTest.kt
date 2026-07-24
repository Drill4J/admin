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
package com.epam.drill.admin.writer.rawdata

import com.epam.drill.admin.test.DatabaseTests
import com.epam.drill.admin.test.withRollback
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.repository.impl.RawMethodBrowseRepositoryImpl
import com.epam.drill.admin.writer.rawdata.service.impl.buildRawMethodTree
import com.epam.drill.admin.writer.rawdata.table.BuildMethodTable
import com.epam.drill.admin.writer.rawdata.table.MethodIgnoreRulesTable
import com.epam.drill.admin.writer.rawdata.table.MethodTable
import com.epam.drill.admin.writer.rawdata.views.RAW_METHOD_TREE_TYPE_CLASS
import com.epam.drill.admin.writer.rawdata.views.RAW_METHOD_TREE_TYPE_METHOD
import com.epam.drill.admin.writer.rawdata.views.RAW_METHOD_TREE_TYPE_PACKAGE
import org.jetbrains.exposed.sql.insert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RawMethodBrowseRepositoryTest : DatabaseTests({ RawDataWriterDatabaseConfig.init(it) }) {
    private val repository = RawMethodBrowseRepositoryImpl()

    @Test
    fun `tree keeps ignored methods visible via aggregates`() = withRollback {
        insertMethod("method-1", "sample/pkg/Example", "getValue")
        insertMethod("method-2", "sample/pkg/Example", "setValue")
        insertMethod("method-3", "sample/other/Other", "run")

        MethodIgnoreRulesTable.insert {
            it[groupId] = GROUP
            it[appId] = APP
            it[classnamePattern] = "sample/pkg/.*"
            it[namePattern] = "get.*"
        }

        val tree = buildRawMethodTree(repository.getMethodLeaves(GROUP, APP, BUILD))
        assertEquals(1, tree.affectedMethods)
        assertEquals(3, tree.totalMethods)
        val pkgNode = tree.roots.single()
        assertEquals(RAW_METHOD_TREE_TYPE_PACKAGE, pkgNode.type)
        assertEquals("sample", pkgNode.name)

        val example = pkgNode.children
            .flatMap { if (it.type == RAW_METHOD_TREE_TYPE_PACKAGE) it.children else listOf(it) }
            .single { it.className == "sample/pkg/Example" }
        assertEquals(RAW_METHOD_TREE_TYPE_CLASS, example.type)
        assertEquals(2, example.totalMethods)
        assertEquals(1, example.ignoredMethods)
        assertEquals(2, example.children.size)
        assertTrue(example.children.all { it.type == RAW_METHOD_TREE_TYPE_METHOD })
        val ignoredLeaf = example.children.single { it.methodName == "getValue" }
        assertTrue(ignoredLeaf.ignored)
        assertEquals("method-1", ignoredLeaf.methodId)
        assertFalse(example.children.single { it.methodName == "setValue" }.ignored)

        val methods = repository.getMethods(GROUP, APP, BUILD, "sample/pkg/Example", 1, 100)
        assertEquals(2, methods.total)
        assertTrue(methods.data.single { it.methodName == "getValue" }.ignored)
        assertFalse(methods.data.single { it.methodName == "setValue" }.ignored)
    }

    private fun insertMethod(methodId: String, className: String, methodName: String) {
        MethodTable.insert {
            it[MethodTable.methodId] = methodId
            it[MethodTable.groupId] = GROUP
            it[MethodTable.appId] = APP
            it[MethodTable.classname] = className
            it[MethodTable.name] = methodName
            it[MethodTable.params] = ""
            it[MethodTable.returnType] = "void"
            it[MethodTable.bodyChecksum] = "checksum-$methodId"
            it[MethodTable.signature] = "$className:$methodName::void"
            it[MethodTable.probesCount] = 1
        }
        BuildMethodTable.insert {
            it[BuildMethodTable.groupId] = GROUP
            it[BuildMethodTable.appId] = APP
            it[BuildMethodTable.buildId] = BUILD
            it[BuildMethodTable.methodId] = methodId
            it[BuildMethodTable.probesStartPos] = 0
        }
    }

    private companion object {
        const val GROUP = "group"
        const val APP = "app"
        const val BUILD = "group:app:1.0"
    }
}
