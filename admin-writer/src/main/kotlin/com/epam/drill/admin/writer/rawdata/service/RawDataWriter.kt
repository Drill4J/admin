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
package com.epam.drill.admin.writer.rawdata.service

import com.epam.drill.admin.common.principal.User
import com.epam.drill.admin.writer.rawdata.route.payload.*
import com.epam.drill.admin.writer.rawdata.views.MethodIgnoreRuleView

interface RawDataWriter {
    suspend fun saveBuild(buildPayload: BuildPayload)
    suspend fun saveInstance(instancePayload: InstancePayload)
    suspend fun saveMethods(methodsPayload: MethodsPayload)
    suspend fun saveCoverage(coveragePayload: CoveragePayload)
    suspend fun saveTestMetadata(testsPayload: AddTestsPayload)
    suspend fun saveTestDefinitions(testDefinitionsPayload: AddTestDefinitionsPayload)
    suspend fun saveTestLaunches(testLaunchesPayload: AddTestLaunchesPayload)
    suspend fun saveTestSession(sessionPayload: SessionPayload, user: User?)
    suspend fun saveMethodIgnoreRule(rulePayload: MethodIgnoreRulePayload)
    suspend fun getAllMethodIgnoreRules(): List<MethodIgnoreRuleView>
    suspend fun deleteMethodIgnoreRuleById(ruleId: Int)
}
