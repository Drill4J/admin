package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.exception.NotAuthorizedException
import com.epam.drill.admin.auth.exception.UserNotAuthenticatedException
import com.epam.drill.admin.auth.model.Role
import com.epam.drill.admin.auth.principal.User
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

class RoleBasedAuthorization(private val config: Configuration) {
    class Configuration {}

    fun interceptPipeline(
        pipeline: ApplicationCallPipeline,
        roles: Set<Role>
    ) {
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Features, Authentication.ChallengePhase)
        pipeline.insertPhaseAfter(Authentication.ChallengePhase, AuthorizationPhase)

        pipeline.intercept(AuthorizationPhase) {
            val principal = call.authentication.principal<User>() ?: throw UserNotAuthenticatedException()
            if (!roles.contains(principal.role)) {
                throw NotAuthorizedException()
            }
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, RoleBasedAuthorization> {
        override val key = AttributeKey<RoleBasedAuthorization>("RoleBasedAuthorization")
        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): RoleBasedAuthorization {
            val configuration = Configuration().apply(configure)
            return RoleBasedAuthorization(configuration)
        }

        val AuthorizationPhase = PipelinePhase("Authorization")
    }

}

class AuthorizedRouteSelector(private val description: String) : RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Constant
}

fun Route.withRole(vararg role: Role, build: Route.() -> Unit) = authorizedRoute(role.toSet(), build = build)

private fun Route.authorizedRoute(
    roles: Set<Role>,
    build: Route.() -> Unit
): Route {
    val authorizedRoute = createChild(AuthorizedRouteSelector("anyOf (${roles.joinToString(", ")})"))
    application.feature(RoleBasedAuthorization).interceptPipeline(authorizedRoute, roles)
    authorizedRoute.build()
    return authorizedRoute
}