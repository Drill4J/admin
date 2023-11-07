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


import com.epam.drill.common.AgentInfo
import com.epam.drill.plugin.api.AdminData
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.routes.Routes
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.coverage.id
import com.epam.drill.plugins.test2code.global_filter.*
import com.epam.drill.plugins.test2code.group.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.StoreClient
import com.epam.dsm.find.Expr.Companion.ANY
import com.epam.dsm.find.get
import com.epam.dsm.find.getAndMap
import com.epam.dsm.util.logPoolStats
import io.ktor.config.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.Closeable
import java.io.InputStream
import java.util.*
import java.util.concurrent.Executors

internal object AsyncJobDispatcher : CoroutineScope {
    override val coroutineContext =
        Executors.newFixedThreadPool(availableProcessors).asCoroutineDispatcher() + SupervisorJob()
}

/**
 * The all information related to the plugin
 *
 * @param adminData the plugin's part of agent data
 * @param sender the messages sender for the plugin
 * @param storeClient the plugin's datasource client
 * @param agentInfo the information about the agent
 * @param id the plugin ID
 *
 */
@Suppress("unused")
class Plugin(
    adminData: AdminData,
    sender: Sender,
    val storeClient: StoreClient,
    agentInfo: AgentInfo,
    id: String,
    val appConfig : ApplicationConfig
) : AdminPluginPart<Action>(
    id = id,
    agentInfo = agentInfo,
    adminData = adminData,
    sender = sender
), Closeable {
    companion object {
        val json = Json { encodeDefaults = true }
    }

    val agentId = agentInfo.id

    val buildVersion = agentInfo.buildVersion

    internal val logger = logger(agentId)

    internal val state: AgentState get() = _state.value!!

    val agentKey = AgentKey(agentId, buildVersion)

    val sessionHolder: SessionHolder get() = state.sessionHolder

    private val runtimeConfig = RuntimeConfig(id)

    private val _state = atomic<AgentState?>(null)

    /**
     * Initialize the plugin state
     * @features Agent registration
     */
    override suspend fun initialize() {
        logger.debug { "$agentKey initializing from admin..." }
        changeState()
        state.loadFromDb {
            processInitialized()
        }
        createGlobalSession()
    }

    override fun close() {
        _state.getAndUpdate { null }?.close()
    }

    override suspend fun applyPackagesChanges() {
        state.sessionHolderManager.deleteByVersion(agentKey)
        storeClient.removeClassData(agentKey)
        changeState()
    }

    override fun parseAction(
        rawAction: String,
    ): Action = json.decodeFromString(Action.serializer(), rawAction)

    override suspend fun doAction(
        action: Action,
        data: Any?,
    ): ActionResult = when (action) {
        is ToggleBaseline -> toggleBaseline()
        is SwitchActiveScope -> {okResult }
        is RenameScope -> { okResult}
        is ToggleScope -> { okResult }
        is DropScope -> { okResult}

        is CreateFilter -> {
            val newFilter = action.payload
            logger.debug { "creating filter with $newFilter..." }
            val isNotExistFilter = storeClient.findBy<StoredFilter> {
                (StoredFilter::name eq newFilter.name) and (StoredFilter::agentId eq agentId)
            }.get().isEmpty()
            if (isNotExistFilter) {
                calculateFilter(newFilter.toStoredFilter(agentId))
            } else ActionResult(code = StatusCodes.CONFLICT, data = "Filter with this name already exists")
        }

        is DuplicateFilter -> {
            val filterId = action.payload.id
            val filter = storeClient.findById<StoredFilter>(filterId)
            logger.debug { "duplicate name $filter..." }
            filter?.let { storedFilter ->
                val filterName = storedFilter.name
                val maxIndex = storeClient.findBy<StoredFilter> {
                    StoredFilter::name like "$filterName($ANY)"
                }.getAndMap(StoredFilter::name).maxOfOrNull {
                    it.substringAfterLast("(").substringBeforeLast(")").toInt()
                } ?: 0
                val duplicateName = "$filterName(${maxIndex + 1})"
                logger.debug { "from $filterName is created new name: $duplicateName" }
                ActionResult(code = StatusCodes.OK, data = duplicateName)
            } ?: ActionResult(code = StatusCodes.BAD_REQUEST, data = "Filter with this id is not found")
        }

        is UpdateFilter -> {
            val updateFilter = action.payload
            val filterId = updateFilter.id
            storeClient.findById<StoredFilter>(filterId)?.let {
                logger.debug { "updating filter with id '$filterId': $updateFilter" }
                calculateFilter(updateFilter.toStoredFilter(agentId))
            } ?: ActionResult(StatusCodes.BAD_REQUEST, "Can not find the filter with id '$filterId'")
        }

        is ApplyFilter -> {
            val filterId = action.payload.id
            val filter = storeClient.findById<StoredFilter>(filterId)
            logger.debug { "applying $filter..." }
            filter?.let {
                calculateFilter(it)
            } ?: ActionResult(code = StatusCodes.BAD_REQUEST, data = "Filter with this id is not found")
        }

        is DeleteFilter -> {
            val filterId = action.payload.id
            logger.debug { "deleting filter with id '$filterId'" }
            storeClient.deleteById<StoredFilter>(filterId)
            sendFilterUpdates(filterId)
            okResult
        }

        is RemoveBuild -> {
            val version = action.payload.version
            if (version != buildVersion && version != state.coverContext().parentBuild?.agentKey?.buildVersion) {
                storeClient.removeBuildData(AgentKey(agentId, version), state.sessionHolderManager)
                okResult
            } else ActionResult(code = StatusCodes.BAD_REQUEST, data = "Can not remove a current or baseline build")
        }

        is RemovePluginData -> {
            storeClient.removeAllPluginData(agentId)
            okResult
        }

        is UpdateSettings -> updateSettings(action.payload)

        /**
         * @features Running tests
         */
        is AddSessionData -> action.payload.run {
            sessionHolder.sessions[sessionId]?.let { session ->
                ActionResult(
                    code = StatusCodes.OK,
                    data = "Successfully received session data",
                    agentAction = mapOf<String, Any>(
                        "session" to session.id,
                        "data" to this.data,
                        "skipSerialize" to true
                    )
                )
            } ?: ActionResult(StatusCodes.NOT_FOUND, "Active session '$sessionId' not found.")
        }

        is AddCoverage -> action.payload.run {
            sessionHolder.addProbes(sessionId) {
                this.data.map { probes ->
                    ExecClassData(
                        className = probes.name,
                        testId = probes.testId,
                        probes = probes.probes.toBitSet()
                    )
                }
            }?.run {
                okResult
            } ?: ActionResult(StatusCodes.NOT_FOUND, "Active session '$sessionId' not found.")
        }

        is ExportCoverage -> exportCoverage(action.payload.version)
        is ImportCoverage -> (data as? InputStream)?.let {
            importCoverage(it)
        } ?: ActionResult(StatusCodes.BAD_REQUEST, "Error while parsing form-data parameters")

        /**
         * @features Running tests
         */
        is AddTests -> action.payload.run {
            sessionHolder.sessions[sessionId]?.let { session ->
                session.addTests(tests)
                ActionResult(
                    code = StatusCodes.OK,
                    data = "Add test successfully"
                )

            } ?: ActionResult(StatusCodes.NOT_FOUND, "Active session '$sessionId' not found.")
        }

        /**
         * @features Session starting
         */
        is StartNewSession -> action.payload.run {
            val newSessionId = sessionId.ifEmpty(::genUuid)
            val isRealtimeSession = runtimeConfig.realtime && isRealtime
            val labels = labels + Label("Session", newSessionId)
            sessionHolder.createSession(
                newSessionId,
                testType,
                isGlobal,
                isRealtimeSession,
                testName,
                labels
            )
            //Leave okResult as stub (for autotest-agent)
            okResult
        }

        /**
         * @features Session finishing
         */
        is StopSession -> action.payload.run {
            sessionHolder.sessions[sessionId]?.let { session ->
                session.addTests(tests)
                state.saveSession(sessionId)
                    ?: logger.info {
                        "No active session with id $sessionId."
                    }
                okResult
            } ?: ActionResult(StatusCodes.NOT_FOUND, "Active session '$sessionId' not found.")
        }
        is CancelSession -> action.payload.run {
            deprecatedResult
        }

        is StopAllSessions -> {
            okResult
        }

        is CancelAllSessions -> {
            okResult
        }

        else -> "Action '$action' is not supported!".let { message ->
            logger.error { message }
            ActionResult(StatusCodes.BAD_REQUEST, message)
        }

    }

    private suspend fun Plugin.calculateFilter(filter: StoredFilter): ActionResult {
        val filterId = calculateAndSendFilteredCoverageInBuild(filter)
        storeClient.store(filter)
        sendFilterUpdates(filterId, filter)
        return ActionResult(code = StatusCodes.OK, data = filterId)
    }

    /**
     * Process data from agents
     * @param instanceId the agent instance ID
     * @param content data to be processed
     */
    override suspend fun processData(
        instanceId: String,
        attachedAgentVersion: String,
        content: String,
    ): Any = run {
        if (attachedAgentVersion != buildVersion) return "";

        val message = if (content.isJson())
            json.decodeFromString(CoverMessage.serializer(), content)
        else {
            val decode = Base64.getDecoder().decode(content)
            ProtoBuf.decodeFromByteArray(CoverMessage.serializer(), decode)
        }
        processData(instanceId, message)
            .let { "" } //TODO eliminate magic empty strings from API
    }

    suspend fun processData(
        instanceId: String,
        message: CoverMessage,
    ) = when (message) {
        is InitInfo -> {
            state.init()
            logger.info { "$instanceId: ${message.message}" } //log init message
            logger.info { "$instanceId: ${message.classesCount} classes to load" }
        }
        /**
         * @features Class data sending
         */
        is InitDataPart -> {
            (state.data as? DataBuilder)?.also {
                logger.info { "$instanceId: $message" }
                it += message.astEntities
            }
        }

        is Initialized -> state.initialized {
            processInitialized()
        }
        /**
         * @features Session starting
         */
        is SessionStarted -> logger.info { "$instanceId: Agent session ${message.sessionId} started." }
            .also { logPoolStats() }

        is CoverDataPart -> {
            message.data
                .groupBy { it.sessionId }
                .forEach { (probeSessionId, data) ->
                    val sessionId = probeSessionId ?: message.sessionId ?: GLOBAL_SESSION_ID
                    if (sessionHolder.sessions[sessionId] == null) {
                        sessionHolder.createSession(sessionId = sessionId, testType = DEFAULT_TEST_TYPE, isRealtime = true, isGlobal = sessionId == GLOBAL_SESSION_ID)
                    }
                    sessionHolder.sessions[sessionId]?.let {
                        sessionHolder.addProbes(sessionId) { data }
                    } ?: logger.debug { "Attempting to add coverage in non-existent active session with id $sessionId" }
                }
        }

        is SessionChanged -> {
            okResult
        }

        is SessionCancelled -> logger.info { "$instanceId: Agent session ${message.sessionId} cancelled." }

        is SessionsCancelled -> logger.info { "$instanceId: Agent sessions cancelled: ${message.ids}." }

        /**
         * @features Session finishing
         */
        is SessionFinished -> {
            delay(500L) //TODO remove after multi-instance core is implemented
            state.saveSession(message.sessionId) ?: logger.info {
                "$instanceId: No active session with id ${message.sessionId}."
            }
        }
        is SessionsFinished -> {
            delay(500L) //TODO remove after multi-instance core is implemented
            message.ids.forEach { state.saveSession(it) }
        }

        //TODO EPMDJ-10398 send on agent attach
        /**
         * Find inactive sessions and send them for cancellation on the agent side
         * @features Agent attaching
         */
        is SyncMessage -> message.run {
            logger.info { "Active session ids from agent: $activeSessions" }
            activeSessions.filter { it !in sessionHolder.sessions }.forEach {
                AsyncJobDispatcher.launch {
                    logger.info { "Attempting to cancel session: $it" }
                    sendAgentAction(CancelAgentSession(AgentSessionPayload(it)))
                }
            }
            sessionHolder.sessions.map.filter { it.key !in activeSessions }.forEach { (id, session) ->
                AsyncJobDispatcher.launch {
                    val startSessionPayload = StartSessionPayload(
                        sessionId = id,
                        testType = session.testType,
                        testName = session.testName,
                        isGlobal = session.isGlobal,
                        isRealtime = session.isRealtime,
                    )
                    logger.info { "Attempting to start session: $startSessionPayload" }
                    sendAgentAction(StartAgentSession(startSessionPayload))
                }
            }
        }

        else -> logger.info { "$instanceId: Message is not supported! $message" }
    }

    /**
     * Saves sessions from SessionHolder to DB with a specified interval
     * @features  Session saving
     */
    private fun finishSessionJob() = AsyncJobDispatcher.launch {
        while (isActive) {
            sessionHolder.sessions.values.forEach { activeSession ->
                state.saveSession(activeSession.id)
            }
            delay(SAVE_DATA_JOB_INTERVAL_MS)
        }
    }

    /**
     * Recalculate build coverage with a specified interval
     */
    private fun calculateMetricsJob() = AsyncJobDispatcher.launch {
        while (isActive) {
            calculateAndSendBuildCoverage()
            delay(METRICS_JOB_INTERVAL_MS)
        }
    }


    /**
     * Initialize the plugin.
     * Send information to the admin UI
     * @features Agent registration
     */
    private suspend fun Plugin.processInitialized() {
        initGateSettings()

        sendGateSettings()
        sendParentBuild()
        sendBaseline()
        sendParentTestsToRunStats()
        state.classDataOrNull()?.sendBuildStats()
        sendLabels()
        sendFilters()
        sendActiveSessions()
        
        finishSessionJob()
        calculateMetricsJob()
    }

    /**
     * Send a parent build version to the UI
     * @features Agent registration
     */
    private suspend fun sendParentBuild() = send(
        buildVersion,
        destination = Routes.Data().let(Routes.Data::Parent),
        message = state.coverContext().parentBuild?.agentKey?.buildVersion?.let(::BuildVersionDto) ?: ""
    )

    /**
     * Send a baseline build version to the UI
     * @features Agent registration
     */
    internal suspend fun sendBaseline() = send(
        buildVersion,
        destination = Routes.Data().let(Routes.Data::Baseline),
        message = storeClient.findById<GlobalAgentData>(agentId)?.baseline?.version?.let(::BuildVersionDto) ?: ""
    )

    /**
     * Send a test to run summary to the UI
     * @features Agent registration
     */
    private suspend fun sendParentTestsToRunStats() = send(
        buildVersion,
        destination = Routes.Build().let(Routes.Build::TestsToRun)
            .let(Routes.Build.TestsToRun::ParentTestsToRunStats),
        message = state.storeClient.loadTestsToRunSummary(
            agentKey = agentKey,
            parentVersion = state.coverContext().build.parentVersion
        ).map { it.toTestsToRunSummaryDto() }
    )

    /**
     * Send build statistics to the UI
     * @features Agent registration
     */
    private suspend fun ClassData.sendBuildStats() {
        send(buildVersion, Routes.Data().let(Routes.Data::Build), state.coverContext().toBuildStatsDto())
    }


    /**
     * Send all active sessions to the UI
     * @features Session saving, Session starting
     */
    internal suspend fun sendActiveSessions() {
        val sessions = sessionHolder.sessions.values.map {
            ActiveSessionDto(
                id = it.id,
                agentId = agentId,
                testType = it.testType,
                isGlobal = it.isGlobal,
                isRealtime = it.isRealtime
            )
        }
        val serviceGroup = agentInfo.serviceGroup
        if (serviceGroup.any()) {
            val aggregatedSessions = sessionAggregator(serviceGroup, agentId, sessions) ?: sessions
            sendToGroup(
                destination = Routes.Group.ActiveSessions(Routes.Group()),
                message = aggregatedSessions
            )
        }
    }


    /**
     * filter in the current build using:
     * @see calculateAndSendBuildCoverage
     */
    private suspend fun calculateAndSendFilteredCoverageInBuild(
        filter: StoredFilter,
    ): String {
        logger.debug { "starting to calculate coverage by $filter..." }
        val initContext = storeClient.findById<InitCoverContext>(agentKey)
        var context = initContext?.let {
            logger.debug { "init tests2run: ${initContext.testsToRun}" }
            logger.trace { "init methodChanges: ${initContext.methodChanges}" }
            state.coverContext().copy(methodChanges = it.methodChanges, testsToRun = it.testsToRun)
        } ?: state.coverContext().copy()

        val bundleCounters = filter.attributes.calcBundleCounters(
            context,
            storeClient,
            agentKey
        )
        context = context.updateBundleCounters(bundleCounters)
        logger.debug { "Starting to calculate coverage by filter..." }
        val filterId = filter.id
        bundleCounters.calculateAndSendBuildCoverage(
            context = context,
            filterId = filterId
        )
        return filterId
    }


    /**
     * Recalculate test coverage data by session and send it to the UI
     * @features Session saving
     */
    private suspend fun calculateAndSendBuildCoverage() {
        val sessions = (sessionHolder.sessions.values).asSequence()
        sessions.calculateAndSendBuildCoverage(state.coverContext())
    }

    /**
     * Calculate coverage data by session
     * @features Session saving
     */
    private suspend fun Sequence<Session>.calculateAndSendBuildCoverage(context: CoverContext) {
        logger.debug { "Start to calculate BundleCounters of build" }
        val bundleCounters = this.calcBundleCounters(context)
        state.updateBundleCounters(bundleCounters)
        logger.debug { "Start to calculate build coverage" }
        bundleCounters.calculateAndSendBuildCoverage(context)
    }

    private suspend fun BundleCounters.calculateAndSendBuildCoverage(
        context: CoverContext,
        filterId: String = "",
    ) {
        val coverageInfoSet = this.calculateCoverageData(context)
        val parentCoverageCount = context.parentBuild?.let { context.parentBuild.stats.coverage } ?: zeroCount
        val risks = context.calculateRisks(storeClient, all)
        val buildCoverage = (coverageInfoSet.coverage as BuildCoverage).copy(
            riskCount = Count(risks.notCovered().count(), risks.count())
        )
        val testsNew = byTestOverview.asSequence().groupBy({ it.key.type }, { TestData(it.key.id, it.value.details) })
        //todo EPMDJ-8975 refactoring this:
        // Option 1: add filterContext as a field of State.
        // Option 2: Move working of Context in another file.
        val newContext = if (filterId.isEmpty()) {
            state.updateBuildStats(buildCoverage, context)
            state.updateBuildTests(testsNew)
            state.coverContext()
        } else {
            context.copy(
                build = context.build.copy(
                    stats = buildCoverage.toCachedBuildStats(context),
                    tests = context.build.tests.copy(tests = testsNew)
                )
            )
        }
        val summary = newContext.build.toSummary(
            agentInfo.name,
            newContext.testsToRun,
            risks,
            coverageInfoSet.coverageByTests,
            coverageInfoSet.tests,
            parentCoverageCount
        )

        coverageInfoSet.sendBuildCoverage(buildVersion, buildCoverage, summary, filterId, newContext)
        assocTestsJob(filterId = filterId)
        coveredMethodsJob(filterId = filterId)
        if (filterId.isEmpty()) {
            state.storeBuild()
            sendGroupSummary(summary)
        }
        val stats = summary.toStatsDto()
        val qualityGate = checkQualityGate(stats)
        send(buildVersion, Routes.Build().let(Routes.Build::Summary), summary.toDto(), filterId)
        Routes.Data().let {
            send(buildVersion, Routes.Data.Stats(it), stats, filterId)
            send(buildVersion, Routes.Data.QualityGate(it), qualityGate, filterId)
            send(buildVersion, Routes.Data.Recommendations(it), summary.recommendations(), filterId)
            send(buildVersion, Routes.Data.Tests(it), summary.tests.toDto(), filterId)
            send(buildVersion, Routes.Data.TestsToRun(it), summary.testsToRun.toDto(), filterId)
        }
    }

    private suspend fun CoverageInfoSet.sendBuildCoverage(
        buildVersion: String,
        buildCoverage: BuildCoverage,
        summary: AgentSummary,
        filterId: String,
        context: CoverContext,
    ) = Routes.Build().let { buildRoute ->
        val coverageRoute = Routes.Build.Coverage(buildRoute)
        send(buildVersion, coverageRoute, buildCoverage, filterId)
        val methodSummaryDto = buildMethods.toSummaryDto().copy(risks = summary.riskCounts)
        send(buildVersion, Routes.Build.Methods(buildRoute), methodSummaryDto, filterId)
        sendBuildTree(packageCoverage, associatedTests.getAssociatedTests(), filterId)
        send(buildVersion, Routes.Build.Tests(buildRoute), tests, filterId)
        Routes.Build.Summary.Tests(Routes.Build.Summary(buildRoute)).let {
            send(buildVersion, Routes.Build.Summary.Tests.All(it), coverageByTests.all, filterId)
            send(buildVersion, Routes.Build.Summary.Tests.ByType(it), coverageByTests.byType, filterId)
        }
        send(buildVersion, Routes.Build.Risks(buildRoute), context.risksDto(storeClient), filterId)
        send(buildVersion, Routes.Build.TestsToRun(buildRoute), context.testsToRunDto(), filterId)
        val testsToRunSummary = context.toTestsToRunSummary()
        if (filterId.isEmpty()) {
            testsToRunSummary.sendTotalSavedTime()
            state.storeClient.store(testsToRunSummary)
        }
        Routes.Build.Summary(buildRoute).let {
            send(
                buildVersion,
                Routes.Build.Summary.TestsToRun(it),
                testsToRunSummary.toTestsToRunSummaryDto(),
                filterId
            )
        }
    }

    private suspend fun sendBuildTree(
        treeCoverage: List<JavaPackageCoverage>,
        associatedTests: List<AssociatedTests>,
        filterId: String,
    ) {
        val coverageRoute = Routes.Build.Coverage(Routes.Build())
        val pkgsRoute = Routes.Build.Coverage.Packages(coverageRoute)
        val packages = treeCoverage.takeIf { runtimeConfig.sendPackages } ?: emptyList()
        send(buildVersion, pkgsRoute, packages.map { it.copy(classes = emptyList()) }, filterId)
        packages.forEach {
            AsyncJobDispatcher.launch {
                send(buildVersion, Routes.Build.Coverage.Packages.Package(it.name, pkgsRoute), it, filterId)
            }
        }
        if (associatedTests.isNotEmpty()) {
            logger.info { "Assoc tests - ids count: ${associatedTests.count()}" }
            associatedTests.forEach {
                AsyncJobDispatcher.launch {
                    send(buildVersion, Routes.Build.AssociatedTests(it.id, Routes.Build()), it, filterId)
                }
            }
        }
    }

    private suspend fun Plugin.sendGroupSummary(summary: AgentSummary) {
        val serviceGroup = agentInfo.serviceGroup
        if (serviceGroup.any()) {
            val aggregated = summaryAggregator(serviceGroup, agentId, summary)
            val summaries = summaryAggregator.getSummaries(serviceGroup)
            Routes.Group().let { groupParent ->
                sendToGroup(
                    destination = Routes.Group.Summary(groupParent),
                    message = ServiceGroupSummaryDto(
                        name = serviceGroup,
                        aggregated = aggregated.toDto(),
                        summaries = summaries.map { (agentId, summary) ->
                            summary.toDto(agentId)
                        }
                    )
                )
                Routes.Group.Data(groupParent).let {
                    sendToGroup(
                        destination = Routes.Group.Data.TestsSummaries(it),
                        message = summaries.map { (agentId, summary) ->
                            summary.testsCoverage
                                .filter { tests -> tests.coverage.percentage != 0.0 }
                                .toDto(agentId)
                        }
                    )

                    sendToGroup(
                        destination = Routes.Group.Data.Tests(it),
                        message = aggregated.tests.toDto()
                    )

                    sendToGroup(
                        destination = Routes.Group.Data.TestsToRun(it),
                        message = aggregated.testsToRun.toDto()
                    )
                    sendToGroup(
                        destination = Routes.Group.Data.Recommendations(it),
                        message = aggregated.recommendations()
                    )
                }
            }
        }
    }

    internal suspend fun send(buildVersion: String, destination: Any, message: Any, filterId: String = "") {
        sender.send(AgentSendContext(agentId, buildVersion, filterId), destination, message)
    }

    private suspend fun Plugin.sendAgentAction(message: AgentAction) {
        sender.sendAgentAction(agentId, id, message)
    }

    /**
     * Update agent state of the plugin and close session-holder
     * @features Agent registration
     */
    private fun changeState() {
        logger.debug { "$agentKey changing state..." }
        _state.getAndUpdate {
            AgentState(
                storeClient = storeClient,
                agentInfo = agentInfo,
                adminData = adminData,
            )
        }?.close()
    }

    /**
     * Calculate all associated tests and send to the UI
     * @param filterId the filter
     * @features Agent registration, Test running
     */
    internal suspend fun BundleCounters.assocTestsJob(
        filterId: String = "",
    ) = AsyncJobDispatcher.launch {
        trackTime("assocTestsJob") {
            logger.debug { "Calculating all associated tests..." }
            val assocTestsMap = trackTime("assocTestsJob getAssocTestsMap") {
                associatedTests(onlyPackages = false)
            }
            val associatedTests = trackTime("assocTestsJob getAssociatedTests") {
                assocTestsMap.getAssociatedTests()
            }
            val treeCoverage = trackTime("assocTestsJob getTreeCoverage") {
                state.coverContext().packageTree.packages.treeCoverage(all, assocTestsMap)
            }
            logger.debug { "Sending all associated tests..." }
            run {
                send(
                    buildVersion,
                    Routes.Build.Risks(Routes.Build()),
                    state.coverContext().risksDto(storeClient, assocTestsMap),
                    filterId
                )
                trackTime("assocTestsJob sendBuildTree") { sendBuildTree(treeCoverage, associatedTests, filterId) }
            }
        }
    }

    /**
     * Calculate created, modified and deleted methods
     * @features Agent registration, Scope finished, Test running
     */
    internal suspend fun BundleCounters.coveredMethodsJob(
        context: CoverContext = state.coverContext(),
        filterId: String = "",
    ) = AsyncJobDispatcher.launch {
        trackTime("coveredByTestJob") {
            byTest.entries.parallelStream().forEach { (testKey, bundle) ->
                val testId = testKey.id()
                val typedTest = byTestOverview[testKey]?.details?.typedTest(testKey.type) ?: TypedTest(testKey.type)
                val coveredMethods = context.toCoverMap(bundle, true)
                val summary = coveredMethods.toSummary(testId, typedTest, context)
                val all = coveredMethods.values.toList()
                val modified = coveredMethods.filterValues { it in context.methodChanges.modified }
                val new = coveredMethods.filterValues { it in context.methodChanges.new }
                val unaffected = coveredMethods.filterValues { it in context.methodChanges.unaffected }
                AsyncJobDispatcher.launch {
                    Routes.Build.MethodsCoveredByTest(testId, Routes.Build()).let { test ->
                        send(buildVersion, Routes.Build.MethodsCoveredByTest.Summary(test), summary, filterId)
                        send(buildVersion, Routes.Build.MethodsCoveredByTest.All(test), all, filterId)
                        send(buildVersion, Routes.Build.MethodsCoveredByTest.Modified(test), modified, filterId)
                        send(buildVersion, Routes.Build.MethodsCoveredByTest.New(test), new, filterId)
                        send(buildVersion, Routes.Build.MethodsCoveredByTest.Unaffected(test), unaffected, filterId)
                    }
                }
            }
        }
    }

    private suspend fun createGlobalSession() {
        doAction(StartNewSession(
            payload = StartPayload(
                testType = GLOBAL_SESSION_ID,
                sessionId = GLOBAL_SESSION_ID,
                testName = GLOBAL_SESSION_ID,
                isRealtime = true,
                isGlobal = true)
            ),
            data = null
        )
        logger.debug { "Global session for agent $agentId was created." }
    }
}
