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
package com.epam.drill.admin.writer.rawdata.entity

import com.epam.drill.admin.writer.rawdata.exception.InvalidMethodIgnoreRule
import java.util.regex.PatternSyntaxException

class MethodIgnoreRule(
    val groupId: String,
    val appId: String,
    val namePattern: String? = null,
    val classnamePattern: String? = null,
    val annotationsPattern: String? = null,
    val classAnnotationsPattern: String? = null
) {
    init {
        validate()
    }

    private fun validate() {
        if (groupId.isEmpty()) {
            throw InvalidMethodIgnoreRule("Field 'groupId' is required and must contain non-empty string")
        }

        if (appId.isEmpty()) {
            throw InvalidMethodIgnoreRule("Field 'appId' is required and must contain non-empty string")
        }

        if (namePattern.isNullOrEmpty() && classnamePattern.isNullOrEmpty() &&
            annotationsPattern.isNullOrEmpty() && classAnnotationsPattern.isNullOrEmpty()) {
            throw InvalidMethodIgnoreRule("You must specify at least one of the following fields containing valid regex: " +
                    "'namePattern', " +
                    "'classnamePattern', " +
                    "'annotationsPattern', " +
                    "'classAnnotationsPattern'")
        }

        namePattern?.let { validateRegex(it, "namePattern") }
        classnamePattern?.let { validateRegex(it, "classnamePattern") }
        annotationsPattern?.let { validateRegex(it, "annotationsPattern") }
        classAnnotationsPattern?.let { validateRegex(it, "classAnnotationsPattern") }
    }

    private fun validateRegex(pattern: String, patternName: String) {
        try {
            Regex(pattern)
        } catch (e: PatternSyntaxException) {
            throw InvalidMethodIgnoreRule("Field '$patternName' contains invalid regex: '$pattern'")
        }
    }
}
