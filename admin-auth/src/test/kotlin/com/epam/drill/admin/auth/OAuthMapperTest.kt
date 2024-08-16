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
import com.epam.drill.admin.auth.config.OAuth2Config
import com.epam.drill.admin.auth.config.OAuthUnauthorizedException
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.service.impl.OAuthMapperImpl
import io.ktor.server.config.*
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

/**
 * Testing [OAuthMapperImpl]
 */
class OAuthMapperTest {

    private val testAlgorithm = Algorithm.HMAC512("secret")
    private val testUsername = "some-username"
    private val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig()))

    @Test
    fun `given token mapping config, mapAccessTokenPayloadToUserEntity must map access token data to user entity`() {
        val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig().apply {
            put("tokenMapping.username", "user_name")
            put("tokenMapping.roles", "realm_roles")
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
        val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig().apply {
            put("userInfoMapping.username", "user_name")
            put("userInfoMapping.roles", "authorities")
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
        val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig().apply {
            put("roleMapping.user", "Dev")
            put("roleMapping.admin", "Ops")
            put("tokenMapping.roles", "roles")
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
        val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig().apply {
            put("roleMapping.user", "Dev")
            put("roleMapping.admin", "Ops")
            put("tokenMapping.roles", "roles")
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
        val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig().apply {
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
        val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig().apply {
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
        val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig().apply {
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
        val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig().apply {
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
    fun `updateDatabaseUserEntity must override Database fields with not null fields from OAuth2`() {
        val mergedUserEntity = oauthMapper.updateDatabaseUserEntity(
            userFromDatabase = UserEntity(username = testUsername, role = Role.USER.name, id = 123),
            userFromOAuth = UserEntity(username = testUsername, role = Role.ADMIN.name)
        )

        assertEquals(Role.ADMIN.name, mergedUserEntity.role)
        assertEquals(123, mergedUserEntity.id)
    }

    @Test
    fun `given undefined role from OAuth2, updateDatabaseUserEntity return user entity with role from Database`() {
        val mergedUserEntity = oauthMapper.updateDatabaseUserEntity(
            userFromDatabase = UserEntity(username = testUsername, role = Role.USER.name),
            userFromOAuth = UserEntity(username = testUsername, role = Role.UNDEFINED.name)
        )

        assertEquals(Role.USER.name, mergedUserEntity.role)
    }

    @Test
    fun `if token mapping username value is not present in token content, mapAccessTokenPayloadToUserEntity must fail`() {
        val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig().apply {
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
        val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig().apply {
            put("userInfoMapping.username", "username")
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

    @Test
    fun `given comma-separated list of roleMapping, mapUserInfoToUserEntity must find first matching role from list of mappings`() {
        val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig().apply {
            put("roleMapping.user", "Developer, Tester")
            put("userInfoMapping.roles", "roles")
        }))
        val userInfo = """
                {
                    "username": "some-username",
                    "roles": ["Tester"]                                               
                }
            """.trimIndent()

        val userEntity = oauthMapper.mapUserInfoToUserEntity(userInfo)

        assertEquals(Role.USER.name, userEntity.role)
    }

    @Test
    fun `given comma-separated list of roleMapping, mapAccessTokenPayloadToUserEntity must find first matching role from list of mappings`() {
        val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig().apply {
            put("roleMapping.admin", "Developer, Ops")
            put("tokenMapping.roles", "roles")
        }))
        val accessToken = JWT.create()
            .withSubject("some-username")
            .withClaim("roles", listOf("Ops"))
            .sign(testAlgorithm)

        val userEntity = oauthMapper.mapAccessTokenPayloadToUserEntity(accessToken)

        assertEquals(Role.ADMIN.name, userEntity.role)
    }

    @Test
    fun `if userinfo contains several roles, mapUserInfoToUserEntity must take role with greater privileges`() {
        val oauthMapper = OAuthMapperImpl(OAuth2Config(MapApplicationConfig().apply {
            put("roleMapping.user", "Dev")
            put("roleMapping.admin", "Ops")
            put("userInfoMapping.roles", "roles")
        }))
        val userInfo = """
                {
                    "username": "some-username",
                    "roles": ["Dev", "Ops"]                                               
                }
            """.trimIndent()

        val userEntity = oauthMapper.mapUserInfoToUserEntity(userInfo)

        assertEquals(Role.ADMIN.name, userEntity.role)
    }
}