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

import com.epam.drill.admin.writer.rawdata.entity.BuildValidationStatus

/**
 * Service for validating builds that have been submitted for finalization.
 */
interface BuildValidationService {
    /**
     * Validates a single build (looked up by [buildId]) that has already been submitted for finalization.
     */
    suspend fun validateBuildById(groupId: String, appId: String, buildId: String): BuildValidationStatus

    /**
     * Re-validates all builds whose finalization is still PENDING and are due for a retry.
     * Called periodically by a fixed-interval job; a build is retried on every call until it is
     * validated successfully or until the maximum validation window (since `finalizedAt`) elapses.
     */
    suspend fun validateAllBuilds()
}
