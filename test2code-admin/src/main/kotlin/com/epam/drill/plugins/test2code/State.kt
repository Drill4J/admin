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
package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.*
import com.epam.dsm.util.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

/**
 * Agent state.
 * The state itself holds only an atomic reference to the data.
 * The data is represented by the sealed class hierarchy AgentData.
 * In case of inconsistencies of the data a ClassCastException is thrown.
 */


internal class AgentState(
    val storeClient: StoreClient,
    val agentInfo: AgentInfo,
    val adminData: AdminData,
) {
    private val logger = logger(agentInfo.id)

    val data get() = _data.value

    val sessionHolderManager = SessionHolderManager(storeClient)

    val sessionHolder get() = _sessionHolder.value

    val qualityGateSettings = AtomicCache<String, ConditionSetting>()

    private val _data = atomic<AgentData>(DataBuilder())

    private val _coverContext = atomic<CoverContext?>(null)

    private val agentKey = AgentKey(agentInfo.id, agentInfo.buildVersion)
    private val _sessionHolder = atomic(
        SessionHolder(
            agentKey = agentKey
        )
    )



    /**
     * Initialize the agent state and call a function if the agent class data exists in database
     * @param block a function that will be called if the class data exists in the database
     * @features Agent registration
     */
    suspend fun loadFromDb(block: suspend () -> Unit = {}) {
        logger.debug { "starting load ClassData from DB..." }
        storeClient.loadClassData(agentKey)?.let { classData ->
            logger.debug { "take from DB count methods ${classData.methods.size}" }
            _data.value = classData
            initialized(classData)
            block()
        }
    }

    fun init() = _data.update {
        _coverContext.value = null
        DataBuilder()
    }

    fun close() {
        logger.debug { "close sessionHolder id=${sessionHolder.id}" }
        sessionHolder.close()
    }

    private val mutex = Mutex()

    /**
     * Initialize agent data state of the plugin
     * - Converts DataBuilder to ClassData if the data state is DataBuilder class
     * - Load class bytes from the database if the data state is NoData class
     * - Updates class data state
     * - Stores class data to the database
     * - Initializes class data
     * - Call the block
     * @param block the function which will be called in the end
     * @features Agent registration
     */
    suspend fun initialized(block: suspend () -> Unit = {}): Unit = mutex.withLock {
        logger.debug { "initialized by event from agent..." }.also { logPoolStats() }
        _data.getAndUpdate {
            when (it) {
                is ClassData -> it
                else -> ClassData(agentKey)
            }
        }.takeIf { it !is ClassData }?.also { data ->
            val classData = when (data) {
                is DataBuilder -> data.flatMap { e -> e.methodsWithProbes().map { e to it } }.run {
                    logger.debug { "initializing DataBuilder..." }
                    val methods = map { (e, m) ->
                        Method(
                            ownerClass = fullClassname(e.path, e.name),
                            name = m.name.weakIntern(),
                            desc = m.toDesc(),
                            hash = m.checksum.weakIntern()
                        )
                    }.sorted()
                    val packages = data.toPackages()
                    PackageTree(
                        totalCount = sumOf { it.second.count },
                        totalMethodCount = count(),
                        totalClassCount = packages.sumOf { it.totalClassesCount },
                        packages = packages
                    ).toClassData(agentKey, methods = methods)
                }
                is NoData -> {
                    throw UnsupportedOperationException("Java class bytes are not supported")
                }
                else -> data
            } as ClassData
            classData.store(storeClient)
            initialized(classData)
            block()
        }
    }

    /**
     * Initialize the state of the agent data from DB
     * Also calc difference between methods in current and parent build versions
     * @param classData the data of agent classes
     * @features Agent registration
     */
    private suspend fun initialized(classData: ClassData) {
        val build: CachedBuild = storeClient.loadBuild(agentKey) ?: CachedBuild(agentKey)
        val coverContext = CoverContext(
            agentType = agentInfo.agentType,
            packageTree = classData.packageTree,
            methods = classData.methods,
            probeIds = classData.probeIds,
            build = build
        )
        _coverContext.value = coverContext
        val (agentId, buildVersion) = agentKey
        logger.debug { "$agentKey initializing..." }
        storeClient.findById<GlobalAgentData>(agentId)?.baseline?.let { baseline ->
            logger.debug { "(buildVersion=$buildVersion) Current baseline=$baseline." }
            val parentVersion = when (baseline.version) {
                buildVersion -> baseline.parentVersion
                else -> baseline.version
            }.takeIf(String::any)
            logger.debug { "ParentVersion=$parentVersion." }
            parentVersion?.let { storeClient.loadClassData(AgentKey(agentInfo.id, it)) }?.let { parentClassData ->
                val methodChanges = classData.methods.diff(parentClassData.methods)
                val parentBuild = storeClient.loadBuild(AgentKey(agentId, parentVersion))?.run {
                    baseline.parentVersion.takeIf(String::any)?.let {
                        copy(parentVersion = it)
                    } ?: this
                }
                val testsToRun = parentBuild?.run {
                    val baselineRisks = storeClient.loadRisksByBaseline(parentBuild.agentKey)
                    val coveredMethods = baselineRisks.risks.mapNotNull { risk ->
                        risk.method.takeIf { risk.buildStatuses.isNotEmpty() }
                    }
                    val notCoveredMethods = methodChanges.modified.filterNot { coveredMethods.contains(it) }
                    bundleCounters.testsWith(notCoveredMethods)
                }.orEmpty()
                val deletedWithCoverage: Map<Method, Count> = parentBuild?.run {
                    bundleCounters.all.coveredMethods(methodChanges.deleted)
                }.orEmpty()
                val testsToRunParentDurations = parentBuild?.let {
                    TestDurations(
                        all = testsToRun.totalDuration(it.bundleCounters.byTestOverview),
                        byType = testsToRun.mapValues { (type, tests) ->
                            mapOf(type to tests).totalDuration(it.bundleCounters.byTestOverview)
                        }
                    )
                } ?: TestDurations(all = 0L, byType = emptyMap())
                logger.debug { "testsToRun parent durations $testsToRunParentDurations" }
                val diffMethods = methodChanges.copy(deletedWithCoverage = deletedWithCoverage)
                storeClient.store(InitCoverContext(agentKey, diffMethods, testsToRun))
                _coverContext.value = coverContext.copy(
                    methodChanges = diffMethods,
                    build = build.copy(parentVersion = parentVersion),
                    parentBuild = parentBuild,
                    testsToRun = testsToRun,
                    testsToRunParentDurations = testsToRunParentDurations
                )
            }
        } ?: run {
            val baseline = Baseline(buildVersion)
            storeClient.store(GlobalAgentData(agentId, baseline))
            logger.debug { "(buildVersion=$buildVersion) Stored initial baseline $baseline." }
        }
        initSessionHolder()
    }

    /**
     * That job each 5 seconds will save all sessions from SessionHolder to DB
     * @features  Session saving
     */
    fun sessionFinishingJob() = CoroutineScope(Dispatchers.Default).launch {
        while (isActive) {
            delay(10000)
            sessionHolder.sessions.values.forEach { activeSession ->
                finishSession(activeSession.id)
            }
        }
    }

    /**
     * Finish the test session
     * @param sessionId the session ID which need to finish
     * @features Session finishing, Session saving
     */
    internal suspend fun finishSession(
        sessionId: String,
    ): TestSession? = sessionHolder.sessions[sessionId]?.also { testSession ->
        if (testSession.any()) {
            logger.debug {
                "FinishSession. size of exec data = ${testSession.probes.values.map { it.keys }.count()}"
            }.also { logPoolStats() }
            trackTime("session storing") {
                storeClient.storeSession(
                    sessionHolder.id,
                    agentKey,
                    testSession
                )
            }
            logger.debug { "Session $sessionId finished." }.also { logPoolStats() }
        } else logger.debug { "Session with id $sessionId is empty, it won't be added to the sessionHolder." }
    }

    internal fun updateBundleCounters(
        bundleCounters: BundleCounters,
    ): CachedBuild = updateBuild {
        copy(bundleCounters = bundleCounters)
    }

    internal fun updateBuildTests(
        tests: GroupedTests,
    ): CachedBuild = updateBuild {
        copy(
            tests = this.tests.copy(tests = tests)
        )
    }

    internal fun updateBuildStats(
        buildCoverage: BuildCoverage,
        context: CoverContext,
    ): CachedBuild = updateBuild {
        copy(stats = buildCoverage.toCachedBuildStats(context))
    }

    private fun updateBuild(
        updater: CachedBuild.() -> CachedBuild,
    ): CachedBuild = _coverContext.updateAndGet {
        it?.copy(build = it.build.updater())
    }!!.build

    suspend fun storeBuild() {
        trackTime("storeBuild") { _coverContext.value?.build?.store(storeClient) }
    }

    internal fun coverContext(): CoverContext = _coverContext.value!!

    internal fun classDataOrNull(): ClassData? = _data.value as? ClassData

    /**
     * Update an SessionHolder
     * @features Agent registration
     */
    private suspend fun initSessionHolder() {
        readSessionHolderInfo()?.run {
            val loadedSessions = storeClient.loadSessions(id).associateBy { it.id }
            logger.debug { "load sessions for SessionHolder with id=$id" }.also { logPoolStats() }
            _sessionHolder.update {
                SessionHolder(
                    id = id,
                    agentKey = agentKey
                ).apply {
                    sessions.putAll(loadedSessions)
                }
            }
        } ?: storeSessionHolderInfo()
    }

    private suspend fun readSessionHolderInfo(): SessionHolderInfo? = sessionHolderManager.counter(agentKey)

    /**
     * Store the scope to the database
     * @features Session saving
     */
    private suspend fun storeSessionHolderInfo() = trackTime("storeSessionHolderInfo") {
        sessionHolderManager.storeCounter(
            sessionHolder.run {
                SessionHolderInfo(
                    agentKey = AgentKey(agentInfo.id, agentKey.buildVersion),
                    id = id,
                )
            }
        )
    }

    internal suspend fun toggleBaseline(): String? = run {
        val agentId = agentInfo.id
        val buildVersion = agentInfo.buildVersion
        val data = storeClient.findById(agentId) ?: GlobalAgentData(agentId)
        val baseline = data.baseline
        val parentBuild = coverContext().parentBuild
        val parentVersion = coverContext().build.parentVersion
        when (baseline.version) {
            buildVersion -> parentBuild?.let {
                Baseline(
                    version = baseline.parentVersion,
                    parentVersion = it.parentVersion
                )
            }
            parentVersion -> Baseline(
                version = buildVersion,
                parentVersion = parentVersion
            )
            else -> null
        }?.also { newBaseline ->
            storeClient.store(data.copy(baseline = newBaseline))
            logger.debug { "(buildVersion=${agentInfo.buildVersion}) Toggled baseline $baseline->$newBaseline" }
        }?.version
    }
}


fun CoverContext.updateBundleCounters(
    bundleCounters: BundleCounters,
): CoverContext = updateBuild {
    copy(bundleCounters = bundleCounters)
}

private fun CoverContext.updateBuild(
    updater: CachedBuild.() -> CachedBuild,
): CoverContext {
    return copy(build = build.updater())
}
