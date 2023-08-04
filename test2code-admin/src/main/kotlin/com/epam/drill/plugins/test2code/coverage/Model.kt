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
package com.epam.drill.plugins.test2code.coverage

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.dsm.*
import kotlinx.serialization.*

/**
 * Difference in methods between builds
 * @param new the list of added methods
 * @param modified the list of modified methods
 * @param deleted the list of deleted methods
 * @param unaffected the list of unaffected methods
 * @param deletedWithCoverage the list of deleted methods that had code coverage
 */
@Serializable
data class DiffMethods(
    val new: List<Method> = emptyList(),
    val modified: List<Method> = emptyList(),
    val deleted: List<Method> = emptyList(),
    val unaffected: List<Method> = emptyList(),
    val deletedWithCoverage: Map<Method, Count> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean = super.equals(other)

    override fun hashCode(): Int = super.hashCode()
}

/**
 * Duration of the tests
 * @param all total duration of all tests
 * @param byType duration grouped by test type
 */
data class TestDurations(
    val all: Long = 0L,
    val byType: Map<String, Long> = emptyMap(),
)

/**
 * Model for storing filtered risks, methods differences and tests to run
 * @param agentKey the pair of the agent ID and build version
 * @param methodChanges difference in methods between builds
 * @param testsToRun test results grouped by type
 */
@Serializable
data class InitCoverContext(
    @Id val agentKey: AgentKey,
    val methodChanges: DiffMethods = DiffMethods(),
    val testsToRun: GroupedTests = emptyMap(),
)

/**
 * Context of coverage
 * @param agentType the agent type
 * @param packageTree summary of packages, classes and methods
 * @param methods the list of all methods
 * @param methodChanges summary of added, changed, removed methods
 * @param probeIds the map where key is class name and value is a csr value of the class name
 * @param build information about the current build
 * @param parentBuild information about the previous build
 * @param testsToRun the map of testing data
 * @param testsToRunParentDurations duration of tests performed
 */
data class CoverContext(
    val agentType: String,
    val packageTree: PackageTree,
    val methods: List<Method>,
    val methodChanges: DiffMethods = DiffMethods(),
    val probeIds: Map<String, Long> = emptyMap(),
    val build: CachedBuild,
    val parentBuild: CachedBuild? = null,
    val testsToRun: GroupedTests = emptyMap(),
    val testsToRunParentDurations: TestDurations = TestDurations(),
) {
    override fun equals(other: Any?): Boolean = super.equals(other)

    override fun hashCode(): Int = super.hashCode()
}

/**
 * The key for grouping coverage data
 * @param id the key ID
 * @param packageName the package name if the key by packages
 * @param className the class name if the key by classes
 * @param methodName the method name if the key by methods
 * @param methodDesc the method descriptor if the key by methods
 */
data class CoverageKey(
    val id: String,
    val packageName: String = "",
    val className: String = "",
    val methodName: String = "",
    val methodDesc: String = "",
) {
    override fun equals(other: Any?) = other is CoverageKey && id == other.id

    override fun hashCode() = id.hashCode()
}

/**
 * Various sets of build coverage counters
 * @param all the overall coverage
 * @param testTypeOverlap the overlapping coverage between different test types
 * @param overlap the overlapping coverage between current and previous scopes
 * @param byTestType the coverage separated by test types
 * @param byTest the coverage separated by tests
 * @param byTestOverview information about all tests (auto, manual, tests without coverage)
 */
@Serializable
class BundleCounters(
    val all: BundleCounter,
    val testTypeOverlap: BundleCounter,
    val overlap: BundleCounter,
    val byTestType: Map<String, BundleCounter> = emptyMap(),
    val byTest: Map<TestKey, BundleCounter> = emptyMap(),
    val byTestOverview: Map<TestKey, TestOverview> = emptyMap(),
) {
    companion object {
        val empty = BundleCounter("").let {
            BundleCounters(all = it, testTypeOverlap = it, overlap = it)
        }
    }
}

sealed class NamedCounter {
    abstract val name: String
    abstract val count: Count
}

/**
 * Build coverage counters
 * @param name the build name
 * @param count the number of covered and all probes in the build
 * @param methodCount the number of covered and all methods in the build
 * @param classCount the number of covered and all classes in the build
 * @param packageCount the number of covered and all packages in the build
 * @param packages the list of package coverage counters
 */
@Serializable
data class BundleCounter(
    override val name: String,
    override val count: Count = zeroCount,
    val methodCount: Count = zeroCount,
    val classCount: Count = zeroCount,
    val packageCount: Count = zeroCount,
    val packages: List<PackageCounter> = emptyList(),
) : NamedCounter() {
    companion object {
        val empty = BundleCounter("")
    }
}

/**
 * Package coverage counters
 * @param name the package name
 * @param count the number of covered and all probes
 * @param classCount the number of covered and all classes in the package
 * @param methodCount the number of covered and all methods in the package
 * @param classes the list of class coverage counters
 */
@Serializable
data class PackageCounter(
    override val name: String,
    override val count: Count,
    val classCount: Count,
    val methodCount: Count,
    val classes: List<ClassCounter>,
) : NamedCounter()

/**
 * Class coverage counters
 * @param path the class path
 * @param name the package name
 * @param count the number of covered and all probes
 * @param methods the list of method coverage counters
 * @param fullName the full name of the class (class path + class name)
 * @param probes the list of class probes
 */
@Serializable
data class ClassCounter(
    val path: String,
    override val name: String,
    override val count: Count,
    @DeserializeWithPool val methods: List<MethodCounter>,
    val fullName: String,
    val probes: List<Boolean> = emptyList(),
) : NamedCounter()

/**
 * Method coverage counters
 * @param name the package name
 * @param desc the descriptor of the method
 * @param decl also the descriptor of the method
 * @param sign the generic signature of the method
 * @param fullName the full name of the method (full class name + method name)
 * @param count the number of covered and all probes in the method
 */
@Serializable
data class MethodCounter(
    override val name: String,
    val desc: String,
    val decl: String,
    val sign: String,
    val fullName: String,
    override val count: Count,
) : NamedCounter()
