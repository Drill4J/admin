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
    fun `given token mapping config, mapAccessTokenToUserEntity must map access token data to user entity`() {
        val oauthConfig = OAuthConfig(MapApplicationConfig().apply {
            put("drill.auth.oauth2.tokenMapping.username", "user_name")
            put("drill.auth.oauth2.tokenMapping.roles", "realm_roles")
        })
        val oauthMapper = OAuthMapperImpl(oauthConfig)

        val userEntity = oauthMapper.mapAccessTokenToUserEntity(
            JWT.create()
                .withClaim("user_name", "some-username")
                .withClaim("realm_roles", listOf("user"))
                .sign(testAlgorithm)
        )
        assertEquals("some-username", userEntity.username)
        assertEquals(Role.USER.name, userEntity.role)
    }

    @Test
    fun `given userinfo mapping config, mapUserInfoToUserEntity must map user-info json response to user entity`() {
        val oauthConfig = OAuthConfig(MapApplicationConfig().apply {
            put("drill.auth.oauth2.userInfoMapping.username", "user_name")
            put("drill.auth.oauth2.userInfoMapping.roles", "authorities")
        })
        val oauthMapper = OAuthMapperImpl(oauthConfig)

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
    fun `given role mapping config, mapAccessTokenToUserEntity must map roles from access token to Drill4J roles`() {
        val oauthConfig = OAuthConfig(MapApplicationConfig().apply {
            put("drill.auth.oauth2.roleMapping.user", "Dev")
            put("drill.auth.oauth2.roleMapping.admin", "Ops")
            put("drill.auth.oauth2.tokenMapping.roles", "roles")
        })
        val oauthMapper = OAuthMapperImpl(oauthConfig)

        val userOps = oauthMapper.mapAccessTokenToUserEntity(
            JWT.create()
                .withSubject("user-ops")
                .withClaim("roles", listOf("Ops", "Manager"))
                .sign(testAlgorithm)
        )
        val userDev = oauthMapper.mapAccessTokenToUserEntity(
            JWT.create()
                .withSubject("user-dev")
                .withClaim("roles", listOf("QA", "Dev"))
                .sign(testAlgorithm)
        )

        assertEquals(Role.ADMIN.name, userOps.role)
        assertEquals(Role.USER.name, userDev.role)
    }

    @Test
    fun `if roles cannot be matched, mapAccessTokenToUserEntity must return undefined role`() {
        val oauthConfig = OAuthConfig(MapApplicationConfig().apply {
            put("drill.auth.oauth2.roleMapping.user", "Dev")
            put("drill.auth.oauth2.roleMapping.admin", "Ops")
            put("drill.auth.oauth2.tokenMapping.roles", "roles")
        })
        val oauthMapper = OAuthMapperImpl(oauthConfig)

        val userEntity = oauthMapper.mapAccessTokenToUserEntity(
            JWT.create()
                .withSubject("some-username")
                .withClaim("roles", listOf("QA", "Analyst"))
                .sign(testAlgorithm)
        )

        assertEquals(Role.UNDEFINED.name, userEntity.role)
    }

    @Test
    fun `if token username mapping is not specified, mapAccessTokenToUserEntity must use default username mapping`() {
        val oauthConfig = OAuthConfig(MapApplicationConfig().apply {
            // no username mapping, default value is "sub"
        })
        val oauthMapper = OAuthMapperImpl(oauthConfig)

        val userEntity = oauthMapper.mapAccessTokenToUserEntity(
            JWT.create()
                .withSubject("some-username")  //this is claim "sub"
                .sign(testAlgorithm)
        )
        assertEquals("some-username", userEntity.username)
    }

    @Test
    fun `if userinfo username mapping is not specified, mapUserInfoToUserEntity must use default username mapping`() {
        val oauthConfig = OAuthConfig(MapApplicationConfig().apply {
            // no username mapping, default value is "username"
        })
        val oauthMapper = OAuthMapperImpl(oauthConfig)

        val userEntity = oauthMapper.mapUserInfoToUserEntity(
            """
                {
                    "username": "some-username"                                               
                }
            """.trimIndent()
        )
        assertEquals("some-username", userEntity.username)
    }

    @Test
    fun `if token roles mapping is not specified, mapAccessTokenToUserEntity must return undefined role`() {
        val oauthConfig = OAuthConfig(MapApplicationConfig().apply {
            // no roles mapping, no default value
        })
        val oauthMapper = OAuthMapperImpl(oauthConfig)

        val userEntity = oauthMapper.mapAccessTokenToUserEntity(
            JWT.create()
                .withSubject("some-username")
                .withClaim("roles", listOf("user"))
                .sign(testAlgorithm)
        )
        assertEquals(Role.UNDEFINED.name, userEntity.role)
    }

    @Test
    fun `if userinfo roles mapping is not specified, mapUserInfoToUserEntity must return undefined role`() {
        val oauthConfig = OAuthConfig(MapApplicationConfig().apply {
            // no roles mapping, no default value
        })
        val oauthMapper = OAuthMapperImpl(oauthConfig)

        val userEntity = oauthMapper.mapUserInfoToUserEntity(
            """
                {
                    "username": "some-username",
                    "roles": ["user"]                                                       
                }
            """.trimIndent()
        )
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
    fun `given not matchable username token mapping config, mapAccessTokenToUserEntity must fail`() {
        val oauthConfig = OAuthConfig(MapApplicationConfig().apply {
            put("drill.auth.oauth2.tokenMapping.username", "username")
        })
        val oauthMapper = OAuthMapperImpl(oauthConfig)

        assertThrows<OAuthUnauthorizedException> {
            oauthMapper.mapAccessTokenToUserEntity(
                JWT.create()
                    .withClaim("login", "some-username")
                    .sign(testAlgorithm)
            )
        }
    }

    @Test
    fun `given not matchable username userinfo mapping config, mapUserInfoToUserEntity must fail`() {
        val oauthConfig = OAuthConfig(MapApplicationConfig().apply {
            put("drill.auth.oauth2.userInfoMapping.username", "username")
        })
        val oauthMapper = OAuthMapperImpl(oauthConfig)

        assertThrows<OAuthUnauthorizedException> {
            oauthMapper.mapUserInfoToUserEntity(
                """
                {
                    "login": "some-username"                                                   
                }
            """.trimIndent()
            )
        }
    }
}