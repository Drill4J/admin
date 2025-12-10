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

interface DataManagementService {
    /**
     * Deletes all build data including coverage, instances, methods associated with the specified build.
     * @param groupId The ID of the group.
     * @param appId The ID of the application.
     * @param buildId The ID of the build to delete data for.
     * @param user The user performing the deletion (optional).
     */
    suspend fun deleteBuildData(groupId: String, appId: String, buildId: String, user: User?)
    /**
     * Deletes all test session data including coverage, test launches associated with the specified test session.
     * @param groupId The ID of the group.
     * @param testSessionId The ID of the test session to delete data for.
     * @param user The user performing the deletion (optional).
     */
    suspend fun deleteTestSessionData(groupId: String, testSessionId: String, user: User?)
}