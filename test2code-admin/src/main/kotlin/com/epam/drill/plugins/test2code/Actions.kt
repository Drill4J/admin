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

import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*

internal object StatusCodes {
    const val OK = 200
    const val BAD_REQUEST = 400
    const val NOT_FOUND = 404
    const val CONFLICT = 409
    const val ERROR = 500
    const val NOT_IMPLEMENTED = 501
}

internal val okResult = ActionResult(code = StatusCodes.OK, data = "")

internal fun AgentAction.toActionResult() = ActionResult(
    code = StatusCodes.OK,
    agentAction = this,
    data = this
)

internal fun FieldErrorDto.toActionResult(code: Int) = listOf(this).toActionResult(code)

internal fun List<FieldErrorDto>.toActionResult(code: Int) = ActionResult(
    code = code,
    data = FieldErrorsDto(this)
)
