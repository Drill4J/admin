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
package com.epam.drill.admin.metrics.service.impl

import com.epam.drill.admin.metrics.models.MatViewScope

val lastUpdateStatusView = MatView("metrics.last_update_status")
val buildsView = MatView("metrics.builds", MatViewScope.BUILDS)
val methodsView = MatView("metrics.methods", MatViewScope.BUILDS)
val buildMethodsView = MatView("metrics.build_methods", MatViewScope.BUILDS)

val testLaunchesView = MatView("metrics.test_launches", MatViewScope.TESTS)
val testDefinitionsView = MatView("metrics.test_definitions", MatViewScope.TESTS)
val testSessionsView = MatView("metrics.test_sessions", MatViewScope.TESTS)

val buildClassTestDefinitionCoverageView = MatView("metrics.build_class_test_definition_coverage", MatViewScope.COVERAGE)
val buildMethodTestDefinitionCoverageView = MatView("metrics.build_method_test_definition_coverage", MatViewScope.COVERAGE)
val buildMethodTestSessionCoverageView = MatView("metrics.build_method_test_session_coverage", MatViewScope.COVERAGE)
val buildMethodCoverageView = MatView("metrics.build_method_coverage", MatViewScope.COVERAGE)
val methodCoverageView = MatView("metrics.method_coverage", MatViewScope.COVERAGE)
val testSessionBuildsView = MatView("metrics.test_session_builds", MatViewScope.COVERAGE)
val test2CodeMappingView = MatView("metrics.test_to_code_mapping", MatViewScope.COVERAGE)

class MatView(val name: String, val scope: MatViewScope? = null)
