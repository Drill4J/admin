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
package com.epam.drill.admin.metrics.route

import com.epam.drill.admin.common.config.ApiResponse
import com.epam.drill.admin.common.config.PagedDataResponse
import com.epam.drill.admin.common.config.Paging
import com.epam.drill.admin.metrics.models.SortOrder
import com.epam.drill.admin.metrics.service.MetricsService
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.resources.get
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

@Resource("/test-sessions")
class TestSessions(
    val parent: Groups.GroupId,

    val testTaskIds: List<String> = emptyList(),
    val createdBys: List<String> = emptyList(),
    val results: List<String> = emptyList(),
    val testProjectIds: List<String> = emptyList(),
    val sortBy: String? = null,
    val sortOrder: SortOrder? = null,

    val page: Int? = null,
    val pageSize: Int? = null,
) {
    @Resource("/filter-options")
    class FilterOptions(
        val parent: TestSessions,

        val buildId: String? = null,
        val field: String,
        val query: String? = null,
        val page: Int? = null,
        val pageSize: Int? = null,
    )

    @Resource("/{testSessionId}")
    class TestSessionId(
        val parent: TestSessions,
        val testSessionId: String,
        val buildId: String? = null,
    ) {
        @Resource("/coverage")
        class CoverageSummary(
            val parent: TestSessionId,
            val buildId: String,
            val testDefinitionId: String? = null,
        )

        @Resource("/definitions")
        class Definitions(
            val parent: TestSessionId,
            val buildId: String? = null,
            val query: String? = null,
            val page: Int? = null,
            val pageSize: Int? = null,
        )

        @Resource("/launches")
        class Launches(
            val parent: TestSessionId,
            val buildId: String? = null,
            val path: String? = null,
            val testNames: List<String> = emptyList(),
            val testResults: List<String> = emptyList(),
            val testTags: List<String> = emptyList(),
            val sortBy: String? = null,
            val sortOrder: SortOrder? = null,
            val page: Int? = null,
            val pageSize: Int? = null,
        ) {
            @Resource("/filter-options")
            class FilterOptions(
                val parent: Launches,
            )

            @Resource("page")
            class Page(
                val parent: Launches,
                val launchId: String,
            )
        }

        @Resource("/test-files")
        class FileLaunches(
            val parent: TestSessionId,
            val buildId: String? = null,
            val testPaths: List<String> = emptyList(),
            val results: List<String> = emptyList(),
            val sortBy: String? = null,
            val sortOrder: SortOrder? = null,
            val page: Int? = null,
            val pageSize: Int? = null,
        ) {
            @Resource("/filter-options")
            class FilterOptions(
                val parent: FileLaunches,
                val buildId: String? = null,
                val query: String? = null,
                val page: Int? = null,
                val pageSize: Int? = null,
            )

            @Resource("page")
            class Page(
                val parent: FileLaunches,
                val path: String,
            )
        }

        @Resource("/builds")
        class Builds(
            val parent: TestSessionId,
            val page: Int? = null,
            val pageSize: Int? = null,
        )
    }
}

fun Route.testsRoutes() {
    getTestSessions()
    getTestSessionById()
    getTestSessionBuilds()
    getTestSessionCoverageSummary()
    getTestSessionDefinitions()
    getTestLaunches()
    getTestFileLaunches()
}

fun Route.getTestSessions() {
    val metricsService by closestDI().instance<MetricsService>()

    get<TestSessions> { params ->
        val data = metricsService.getGroupTestSessions(
            groupId = params.parent.groupId,
            testTaskIds = params.testTaskIds,
            createdBys = params.createdBys,
            results = params.results,
            testProjectIds = params.testProjectIds,
            sortBy = params.sortBy,
            sortOrder = params.sortOrder,
            page = params.page,
            pageSize = params.pageSize,
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data.items,
                Paging(data.page, data.pageSize, data.total)
            )
        )
    }

    get<TestSessions.FilterOptions> { params ->
        val data = metricsService.getTestSessionFilterOptions(
            groupId = params.parent.parent.groupId,
            buildId = params.buildId,
            field = params.field,
            query = params.query,
            page = params.page,
            pageSize = params.pageSize,
        )
        this.call.respondPagedStrings(data)
    }
}

fun Route.getTestSessionById() {
    val metricsService by closestDI().instance<MetricsService>()

    get<TestSessions.TestSessionId> { params ->
        val data = metricsService.getTestSessionDetail(
            groupId = params.parent.parent.groupId,
            testSessionId = params.testSessionId,
            buildId = params.buildId,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getTestSessionBuilds() {
    val metricsService by closestDI().instance<MetricsService>()

    get<TestSessions.TestSessionId.Builds> { params ->
        val data = metricsService.getTestSessionBuilds(
            groupId = params.parent.parent.parent.groupId,
            testSessionId = params.parent.testSessionId,
            page = params.page,
            pageSize = params.pageSize,
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data = data.items,
                paging = Paging(
                    page = data.page,
                    pageSize = data.pageSize,
                    total = data.total,
                ),
            )
        )
    }
}

fun Route.getTestSessionCoverageSummary() {
    val metricsService by closestDI().instance<MetricsService>()

    get<TestSessions.TestSessionId.CoverageSummary> { params ->
        val data = metricsService.getTestSessionCoverageSummary(
            groupId = params.parent.parent.parent.groupId,
            testSessionId = params.parent.testSessionId,
            buildId = params.buildId,
            testDefinitionId = params.testDefinitionId,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}


fun Route.getTestSessionDefinitions() {
    val metricsService by closestDI().instance<MetricsService>()

    get<TestSessions.TestSessionId.Definitions> { params ->
        val data = metricsService.getTestSessionDefinitions(
            groupId = params.parent.parent.parent.groupId,
            testSessionId = params.parent.testSessionId,
            buildId = params.buildId,
            query = params.query,
            page = params.page,
            pageSize = params.pageSize,
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data = data.items,
                paging = Paging(
                    page = data.page,
                    pageSize = data.pageSize,
                    total = data.total,
                ),
            )
        )
    }
}

fun Route.getTestLaunches() {
    val metricsService by closestDI().instance<MetricsService>()

    get<TestSessions.TestSessionId.Launches> { params ->
        val data = metricsService.getTestLaunches(
            groupId = params.parent.parent.parent.groupId,
            testSessionId = params.parent.testSessionId,
            buildId = params.buildId,
            path = params.path,
            testNames = params.testNames,
            testResults = params.testResults,
            testTags = params.testTags,
            sortBy = params.sortBy,
            sortOrder = params.sortOrder,
            page = params.page,
            pageSize = params.pageSize,
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data = data.items,
                paging = Paging(
                    page = data.page,
                    pageSize = data.pageSize,
                    total = data.total,
                ),
            )
        )
    }

    get<TestSessions.TestSessionId.Launches.FilterOptions> { params ->
        val launches = params.parent
        val session = launches.parent
        val data = metricsService.getTestLaunchFilterOptions(
            groupId = session.parent.parent.groupId,
            testSessionId = session.testSessionId,
            buildId = launches.buildId,
            path = launches.path,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }

    get<TestSessions.TestSessionId.Launches.Page> { params ->
        val launches = params.parent
        val session = launches.parent
        val data = metricsService.getTestLaunchPage(
            groupId = session.parent.parent.groupId,
            testSessionId = session.testSessionId,
            buildId = launches.buildId,
            path = launches.path,
            testNames = launches.testNames,
            testResults = launches.testResults,
            testTags = launches.testTags,
            sortBy = launches.sortBy,
            sortOrder = launches.sortOrder,
            pageSize = launches.pageSize,
            testDefinitionId = params.launchId,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }

}

fun Route.getTestFileLaunches() {
    val metricsService by closestDI().instance<MetricsService>()

    get<TestSessions.TestSessionId.FileLaunches> { params ->
        val data = metricsService.getTestFileLaunches(
            groupId = params.parent.parent.parent.groupId,
            testSessionId = params.parent.testSessionId,
            buildId = params.buildId,
            testPaths = params.testPaths,
            results = params.results,
            sortBy = params.sortBy,
            sortOrder = params.sortOrder,
            page = params.page,
            pageSize = params.pageSize,
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data = data.items,
                paging = Paging(
                    page = data.page,
                    pageSize = data.pageSize,
                    total = data.total,
                ),
            )
        )
    }

    get<TestSessions.TestSessionId.FileLaunches.FilterOptions> { params ->
        val session = params.parent.parent
        if (params.page != null || params.query != null || params.pageSize != null) {
            val data = metricsService.getTestFileLaunchFilterValues(
                groupId = session.parent.parent.groupId,
                testSessionId = session.testSessionId,
                buildId = params.buildId,
                query = params.query,
                page = params.page,
                pageSize = params.pageSize,
            )
            this.call.respondPagedStrings(data)
            return@get
        }
        val data = metricsService.getTestFileLaunchFilterOptions(
            groupId = session.parent.parent.groupId,
            testSessionId = session.testSessionId,
            buildId = params.buildId,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }

    get<TestSessions.TestSessionId.FileLaunches.Page> { params ->
        val files = params.parent
        val session = files.parent
        val data = metricsService.getTestFileLaunchPage(
            groupId = session.parent.parent.groupId,
            testSessionId = session.testSessionId,
            buildId = files.buildId,
            testPaths = files.testPaths,
            results = files.results,
            sortBy = files.sortBy,
            sortOrder = files.sortOrder,
            pageSize = files.pageSize,
            path = params.path,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

