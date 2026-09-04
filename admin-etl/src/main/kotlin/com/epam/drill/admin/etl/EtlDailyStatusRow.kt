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

import java.time.LocalDate

/**
 * The aggregated [EtlDailyStatus] of a single calendar [day], as returned by
 * [EtlLauncher.getDailyStatuses] / [EtlService.getDailyStatuses].
 */
data class EtlDailyStatusRow(
    val day: LocalDate,
    val status: EtlDailyStatus,
)
