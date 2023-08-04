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
package com.epam.drill.plugins.test2code.storage

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.*
import com.epam.dsm.find.*
import kotlinx.serialization.*

@Serializable
@StreamSerialization
internal class StoredClassData(
    @Id val agentKey: AgentKey,
    val data: ClassData,
)

@Serializable
@StreamSerialization
internal class StoredBundles(
    @Id val agentKey: AgentKey,
    val data: BundleCounters,
)

@Serializable
@StreamSerialization
class StoredBuildTests(
    @Id val agentKey: AgentKey,
    val data: BuildTests,
)

internal suspend fun StoreClient.loadClassData(
    agentKey: AgentKey,
): ClassData? = findById<StoredClassData>(agentKey)?.data

internal suspend fun ClassData.store(storage: StoreClient) {
    trackTime("Store class data") {
        storage.store(StoredClassData(agentKey, this))
    }
}

internal suspend fun StoreClient.removeClassData(agentKey: AgentKey) = deleteById<StoredClassData>(agentKey)

/**
 * Load information about the build
 * @param agentKey the pair of agent ID and build version
 *
 * @features Agent registration
 */
internal suspend fun StoreClient.loadBuild(
    agentKey: AgentKey,
): CachedBuild? = findById<BuildStats>(agentKey)?.let { stats ->
    trackTime("Load build") {
        CachedBuild(
            agentKey = agentKey,
            stats = stats,
            bundleCounters = findById<StoredBundles>(agentKey)?.data ?: BundleCounters.empty,
            tests = findById<StoredBuildTests>(agentKey)?.data ?: BuildTests()
        )
    }
}

internal suspend fun CachedBuild.store(storage: StoreClient) {
    storage.executeInAsyncTransaction {
        trackTime("Store build total") {
            store(stats)
            trackTime("Store build bundles") {
                store(StoredBundles(agentKey, bundleCounters))
            }
            trackTime("Store build tests") {
                store(StoredBuildTests(agentKey, tests))
            }
        }
    }
}

internal suspend fun StoreClient.removeBuild(
    agentKey: AgentKey,
) = executeInAsyncTransaction {
    deleteById<BuildStats>(agentKey)
    deleteById<StoredBundles>(agentKey)
    deleteById<StoredBuildTests>(agentKey)
    deleteById<BaselineRisks>(agentKey)
}


internal suspend fun StoreClient.removeBuildData(
    agentKey: AgentKey,
    scopeManager: ScopeManager,
) = executeInAsyncTransaction {
    trackTime("Remove build $agentKey") {
        logger.debug { "starting to remove build '$agentKey' data..." }
        removeClassData(agentKey)
        removeBuild(agentKey)
        scopeManager.deleteByVersion(agentKey)
    }
}

internal suspend fun StoreClient.removeAllPluginData(
    agent: String,
) = executeInAsyncTransaction {
    trackTime("Remove plugin data for $agent") {
        logger.debug { "starting to remove all plugin data for '$agent'... " }
        deleteBy<StoredClassData> { FieldPath(StoredClassData::agentKey, AgentKey::agentId) eq agent }
        deleteBy<BuildStats> { FieldPath(BuildStats::agentKey, AgentKey::agentId) eq agent }
        deleteBy<StoredBundles> { FieldPath(StoredBundles::agentKey, AgentKey::agentId) eq agent }
        deleteBy<StoredBuildTests> { FieldPath(StoredBuildTests::agentKey, AgentKey::agentId) eq agent }
        deleteBy<BaselineRisks> { FieldPath(BaselineRisks::baseline, AgentKey::agentId) eq agent }
        deleteBy<FinishedScope> { FieldPath(FinishedScope::agentKey, AgentKey::agentId) eq agent }
        deleteBy<ScopeDataEntity> { FieldPath(ScopeDataEntity::agentKey, AgentKey::agentId) eq agent }
        deleteBy<StoredSession> { FieldPath(StoredSession::agentKey, AgentKey::agentId) eq agent }
    }
}

