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
package com.epam.drill.admin.config.route

import com.epam.drill.admin.common.config.ApiResponse
import com.epam.drill.admin.common.route.Groups
import com.epam.drill.admin.config.model.AppImpactGateTargets
import com.epam.drill.admin.config.model.AppOverallGateTargets
import com.epam.drill.admin.config.model.ImpactQualityGateTargets
import com.epam.drill.admin.config.model.MilestonePayload
import com.epam.drill.admin.config.model.OverallQualityGateTargets
import com.epam.drill.admin.config.model.ReleaseTrackPayload
import com.epam.drill.admin.config.service.ConfigService
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

@Serializable
@Resource("/release-tracks")
data class ReleaseTracks(val parent: Groups.GroupId) {
    @Serializable
    @Resource("/list")
    data class List(val parent: ReleaseTracks)

    @Serializable
    @Resource("/{trackId}")
    data class TrackId(val parent: ReleaseTracks, val trackId: String) {
        val groupId: String = parent.parent.groupId
        @Serializable
        @Resource("/milestones")
        data class Milestones(val parent: TrackId) {
            @Serializable
            @Resource("/{milestoneId}")
            data class MilestoneId(val parent: Milestones, val milestoneId: String) {
                val groupId: String = parent.parent.groupId
                val trackId: String = parent.parent.trackId
                @Serializable
                @Resource("/quality-gates/overall")
                data class QualityGateOverall(val parent: MilestoneId)

                @Serializable
                @Resource("/quality-gates/impact")
                data class QualityGateImpact(val parent: MilestoneId)
            }
        }
    }
}

@Serializable
@Resource("/groups/{groupId}/apps")
data class Apps(val groupId: String) {
    @Serializable
    @Resource("/{appId}")
    data class AppId(val parent: Apps, val appId: String) {
        @Serializable
        @Resource("/quality-gates/overall")
        data class QualityGatesOverall(val parent: AppId)

        @Serializable
        @Resource("/quality-gates/impact")
        data class QualityGatesImpact(val parent: AppId)
    }
}

fun Route.configRoutes() {
    val configService by closestDI().instance<ConfigService>()

    post<ReleaseTracks> { params ->
        val payload = call.receive<ReleaseTrackPayload>()
        configService.createTrack(params.parent.groupId, payload.trackId)
        call.respond(HttpStatusCode.OK, ApiResponse(null))
    }

    get<ReleaseTracks> { params ->
        val tracks = configService.getTracks(params.parent.groupId)
        call.respond(HttpStatusCode.OK, ApiResponse(tracks))
    }

    put<ReleaseTracks.TrackId.Milestones> { params ->
        val payload = call.receive<MilestonePayload>()
        configService.saveMilestone(params.parent.groupId, params.parent.trackId, payload)
        call.respond(HttpStatusCode.OK, ApiResponse(null))
    }

    get<ReleaseTracks.TrackId.Milestones> { params ->
        val milestones = configService.getMilestones(params.parent.groupId, params.parent.trackId)
        call.respond(HttpStatusCode.OK, ApiResponse(milestones))
    }

    put<ReleaseTracks.TrackId.Milestones.MilestoneId.QualityGateOverall> { params ->
        val targets = call.receive<OverallQualityGateTargets>()
        configService.saveMilestoneOverallTargets(
            params.parent.groupId,
            params.parent.trackId,
            params.parent.milestoneId,
            targets
        )
        call.respond(HttpStatusCode.OK, ApiResponse(null))
    }

    put<ReleaseTracks.TrackId.Milestones.MilestoneId.QualityGateImpact> { params ->
        val targets = call.receive<ImpactQualityGateTargets>()
        configService.saveMilestoneImpactTargets(
            params.parent.groupId,
            params.parent.trackId,
            params.parent.milestoneId,
            targets
        )
        call.respond(HttpStatusCode.OK, ApiResponse(null))
    }

    get<Apps.AppId.QualityGatesOverall> { params ->
        val targets = configService.getAppOverallTargets(params.parent.parent.groupId, params.parent.appId)
        call.respond(HttpStatusCode.OK, ApiResponse(targets))
    }

    put<Apps.AppId.QualityGatesOverall> { params ->
        val targets = call.receive<AppOverallGateTargets>()
        configService.saveAppOverallTargets(params.parent.parent.groupId, params.parent.appId, targets)
        call.respond(HttpStatusCode.OK, ApiResponse(null))
    }

    get<Apps.AppId.QualityGatesImpact> { params ->
        val targets = configService.getAppImpactTargets(params.parent.parent.groupId, params.parent.appId)
        call.respond(HttpStatusCode.OK, ApiResponse(targets))
    }

    put<Apps.AppId.QualityGatesImpact> { params ->
        val targets = call.receive<AppImpactGateTargets>()
        configService.saveAppImpactTargets(params.parent.parent.groupId, params.parent.appId, targets)
        call.respond(HttpStatusCode.OK, ApiResponse(null))
    }
}
