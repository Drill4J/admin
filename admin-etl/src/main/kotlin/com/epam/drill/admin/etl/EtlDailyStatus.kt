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
package com.epam.drill.admin.etl

/**
 * Represents the daily status of an ETL process.
 */
enum class EtlDailyStatus {
    /** No job has ever been scheduled/run for this day. */
    UNLOADED,

    /** A job covering this day has been scheduled but hasn't started running yet. */
    SCHEDULED,

    /** A job covering this day is currently running. */
    RUNNING,

    /** A job covering this day has finished successfully. */
    COMPLETED,

    /** A job covering this day finished with an error or was cancelled. */
    FAILED,
}
