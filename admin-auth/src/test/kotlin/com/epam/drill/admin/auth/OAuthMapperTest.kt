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
package com.epam.drill.admin.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.epam.drill.admin.auth.config.OAuthConfig
import com.epam.drill.admin.auth.config.OAuthUnauthorizedException
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.service.impl.OAuthMapperImpl
import io.ktor.config.*
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

/**
 * Testing [OAuthMapperImpl]
 */
class OAuthMapperTest {

    private val testAlgorithm = Algorithm.HMAC512("secret")
    private val testUsername = "some-username"
    private val oauthMapper = OAuthMapperImpl(OAuthConfig(MapApplicationConfig()))

    @Test
    fun `given token mapping config, mapAccessTokenPayloadToUserEntity must map access token data to user entity`() {
        val oauthMapper = OAuthMapperImpl(OAuthConfig(MapApplicationConfig().apply {
            put("drill.auth.oauth2.tokenMapping.username", "user_name")
            put("drill.auth.oauth2.tokenMapping.roles", "realm_roles")
        }))
        val externalJwt = JWT.create()
            .withClaim("user_name", "some-username")
            .withClaim("realm_roles", listOf("user"))
            .sign(testAlgorithm)

        val userEntity = oauthMapper.mapAccessTokenPayloadToUserEntity(externalJwt)

        assertEquals("some-username", userEntity.username)
        assertEquals(Role.USER.name, userEntity.role)
    }

    @Test
    fun `given userinfo mapping config, mapUserInfoToUserEntity must map user-info json response to user entity`() {
        val oauthMapper = OAuthMapperImpl(OAuthConfig(MapApplicationConfig().apply {
            put("drill.auth.oauth2.userInfoMapping.username", "user_name")
            put("drill.auth.oauth2.userInfoMapping.roles", "authorities")
        }))

        val userEntity = oauthMapper.mapUserInfoToUserEntity(
            """
                {
                    "user_name": "some-username",
                    "authorities": ["user"]                                                       
                }
            """.trimIndent()
        )
        assertEquals("some-username", userEntity.username)
        assertEquals(Role.USER.name, userEntity.role)
    }

    @Test
    fun `given role mapping config, mapAccessTokenPayloadToUserEntity must map roles from access token to Drill4J roles`() {
        val oauthMapper = OAuthMapperImpl(OAuthConfig(MapApplicationConfig().apply {
            put("drill.auth.oauth2.roleMapping.user", "Dev")
            put("drill.auth.oauth2.roleMapping.admin", "Ops")
            put("drill.auth.oauth2.tokenMapping.roles", "roles")
        }))
        val externalOpsJwt = JWT.create()
            .withSubject("user-ops")
            .withClaim("roles", listOf("Ops", "Manager"))
            .sign(testAlgorithm)
        val externalDevJwt = JWT.create()
            .withSubject("user-dev")
            .withClaim("roles", listOf("QA", "Dev"))
            .sign(testAlgorithm)

        val userOps = oauthMapper.mapAccessTokenPayloadToUserEntity(externalOpsJwt)
        val userDev = oauthMapper.mapAccessTokenPayloadToUserEntity(externalDevJwt)

        assertEquals(Role.ADMIN.name, userOps.role)
        assertEquals(Role.USER.name, userDev.role)
    }

    @Test
    fun `if roles cannot be matched, mapAccessTokenPayloadToUserEntity must return undefined role`() {
        val oauthMapper = OAuthMapperImpl(OAuthConfig(MapApplicationConfig().apply {
            put("drill.auth.oauth2.roleMapping.user", "Dev")
            put("drill.auth.oauth2.roleMapping.admin", "Ops")
            put("drill.auth.oauth2.tokenMapping.roles", "roles")
        }))
        val externalJwt = JWT.create()
            .withSubject("some-username")
            .withClaim("roles", listOf("QA", "Analyst"))
            .sign(testAlgorithm)

        val userEntity = oauthMapper.mapAccessTokenPayloadToUserEntity(externalJwt)

        assertEquals(Role.UNDEFINED.name, userEntity.role)
    }

    @Test
    fun `if token username mapping is not specified, mapAccessTokenPayloadToUserEntity must use default username mapping`() {
        val oauthMapper = OAuthMapperImpl(OAuthConfig(MapApplicationConfig().apply {
            // no username mapping, default value is "sub"
        }))
        val externalJwt = JWT.create()
            .withSubject("some-username")  //this is claim "sub"
            .sign(testAlgorithm)

        val userEntity = oauthMapper.mapAccessTokenPayloadToUserEntity(externalJwt)

        assertEquals("some-username", userEntity.username)
    }

    @Test
    fun `if userinfo username mapping is not specified, mapUserInfoToUserEntity must use default username mapping`() {
        val oauthMapper = OAuthMapperImpl(OAuthConfig(MapApplicationConfig().apply {
            // no username mapping, default value is "username"
        }))
        val userInfo = """
                {
                    "username": "some-username"                                               
                }
            """.trimIndent()

        val userEntity = oauthMapper.mapUserInfoToUserEntity(userInfo)

        assertEquals("some-username", userEntity.username)
    }

    @Test
    fun `if token roles mapping is not specified, mapAccessTokenPayloadToUserEntity must return undefined role`() {
        val oauthMapper = OAuthMapperImpl(OAuthConfig(MapApplicationConfig().apply {
            // no roles mapping, no default value
        }))
        val externalJwt = JWT.create()
            .withSubject("some-username")
            .withClaim("roles", listOf("user"))
            .sign(testAlgorithm)

        val userEntity = oauthMapper.mapAccessTokenPayloadToUserEntity(externalJwt)

        assertEquals(Role.UNDEFINED.name, userEntity.role)
    }

    @Test
    fun `if userinfo roles mapping is not specified, mapUserInfoToUserEntity must return undefined role`() {
        val oauthMapper = OAuthMapperImpl(OAuthConfig(MapApplicationConfig().apply {
            // no roles mapping, no default value
        }))
        val userInfo = """
                {
                    "username": "some-username",
                    "roles": ["user"]                                                       
                }
            """.trimIndent()

        val userEntity = oauthMapper.mapUserInfoToUserEntity(userInfo)

        assertEquals(Role.UNDEFINED.name, userEntity.role)
    }

    @Test
    fun `mergeUserEntities must override Database fields with not null fields from OAuth2`() {
        val mergedUserEntity = oauthMapper.mergeUserEntities(
            userFromDatabase = UserEntity(username = testUsername, role = Role.USER.name, id = 123),
            userFromOAuth = UserEntity(username = testUsername, role = Role.ADMIN.name)
        )

        assertEquals(Role.ADMIN.name, mergedUserEntity.role)
        assertEquals(123, mergedUserEntity.id)
    }

    @Test
    fun `given undefined role from OAuth2, mergeUserEntities return user entity with role from Database`() {
        val mergedUserEntity = oauthMapper.mergeUserEntities(
            userFromDatabase = UserEntity(username = testUsername, role = Role.USER.name),
            userFromOAuth = UserEntity(username = testUsername, role = Role.UNDEFINED.name)
        )

        assertEquals(Role.USER.name, mergedUserEntity.role)
    }

    @Test
    fun `given not matchable username token mapping config, mapAccessTokenPayloadToUserEntity must fail`() {
        val oauthMapper = OAuthMapperImpl(OAuthConfig(MapApplicationConfig().apply {
            put("drill.auth.oauth2.tokenMapping.username", "username")
        }))
        val externalJwt = JWT.create()
            .withClaim("login", "some-username")
            .sign(testAlgorithm)

        assertThrows<OAuthUnauthorizedException> {
            oauthMapper.mapAccessTokenPayloadToUserEntity(externalJwt)
        }
    }

    @Test
    fun `given not matchable username userinfo mapping config, mapUserInfoToUserEntity must fail`() {
        val oauthMapper = OAuthMapperImpl(OAuthConfig(MapApplicationConfig().apply {
            put("drill.auth.oauth2.userInfoMapping.username", "username")
        }))
        val userInfo = """
                {
                    "login": "some-username"                                                   
                }
            """.trimIndent()

        assertThrows<OAuthUnauthorizedException> {
            oauthMapper.mapUserInfoToUserEntity(userInfo)
        }
    }
}