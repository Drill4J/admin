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
import com.epam.drill.admin.common.route.Apps
import com.epam.drill.admin.common.route.Milestones
import com.epam.drill.admin.common.route.ReleaseTracks
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
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

@Resource("/list")
class ReleaseTrackList(val parent: ReleaseTracks)

@Resource("/list")
class MilestoneList(val parent: Milestones)

@Resource("/quality-gates")
class MilestoneQualityGates(val parent: Milestones.MilestoneId) {
    @Resource("/overall")
    class Overall(val parent: MilestoneQualityGates)

    @Resource("/impact")
    class Impact(val parent: MilestoneQualityGates)
}

@Resource("/quality-gates")
class AppQualityGates(val parent: Apps.AppId) {
    @Resource("/overall")
    class Overall(val parent: AppQualityGates)

    @Resource("/impact")
    class Impact(val parent: AppQualityGates)
}

fun Route.configRoutes() {
    val configService by closestDI().instance<ConfigService>()

    post<ReleaseTracks> { params ->
        val payload = call.receive<ReleaseTrackPayload>()
        configService.createTrack(params.parent.groupId, payload.trackId)
        call.respond(HttpStatusCode.OK, ApiResponse(null))
    }

    get<ReleaseTrackList> { params ->
        val tracks = configService.getTracks(params.parent.parent.groupId)
        call.respond(HttpStatusCode.OK, ApiResponse(tracks))
    }

    put<Milestones> { params ->
        val payload = call.receive<MilestonePayload>()
        configService.saveMilestone(params.parent.groupId, params.parent.trackId, payload)
        call.respond(HttpStatusCode.OK, ApiResponse(null))
    }

    get<MilestoneList> { params ->
        val milestones = configService.getMilestones(params.parent.parent.groupId, params.parent.parent.trackId)
        call.respond(HttpStatusCode.OK, ApiResponse(milestones))
    }

    put<MilestoneQualityGates.Overall> { params ->
        val targets = call.receive<OverallQualityGateTargets>()
        configService.saveMilestoneOverallTargets(
            params.parent.parent.groupId,
            params.parent.parent.trackId,
            params.parent.parent.milestoneId,
            targets
        )
        call.respond(HttpStatusCode.OK, ApiResponse(null))
    }

    put<MilestoneQualityGates.Impact> { params ->
        val targets = call.receive<ImpactQualityGateTargets>()
        configService.saveMilestoneImpactTargets(
            params.parent.parent.groupId,
            params.parent.parent.trackId,
            params.parent.parent.milestoneId,
            targets
        )
        call.respond(HttpStatusCode.OK, ApiResponse(null))
    }

    get<AppQualityGates.Overall> { params ->
        val targets = configService.getAppOverallTargets(params.parent.parent.groupId, params.parent.parent.appId)
        call.respond(HttpStatusCode.OK, ApiResponse(targets))
    }

    put<AppQualityGates.Overall> { params ->
        val targets = call.receive<AppOverallGateTargets>()
        configService.saveAppOverallTargets(params.parent.parent.groupId, params.parent.parent.appId, targets)
        call.respond(HttpStatusCode.OK, ApiResponse(null))
    }

    get<AppQualityGates.Impact> { params ->
        val targets = configService.getAppImpactTargets(params.parent.parent.groupId, params.parent.parent.appId)
        call.respond(HttpStatusCode.OK, ApiResponse(targets))
    }

    put<AppQualityGates.Impact> { params ->
        val targets = call.receive<AppImpactGateTargets>()
        configService.saveAppImpactTargets(params.parent.parent.groupId, params.parent.parent.appId, targets)
        call.respond(HttpStatusCode.OK, ApiResponse(null))
    }
}
