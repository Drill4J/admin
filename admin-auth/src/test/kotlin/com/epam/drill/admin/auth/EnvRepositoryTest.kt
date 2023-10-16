package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.repository.impl.EnvUserRepository
import com.epam.drill.admin.auth.service.PasswordService
import io.ktor.config.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.test.*

private const val USER1 = "{\"username\": \"user\", \"password\": \"secret1\", \"role\": \"USER\"}"
private const val USER2 = "{\"username\": \"admin\", \"password\": \"secret2\", \"role\": \"ADMIN\"}"

class EnvRepositoryTest {
    @Mock
    lateinit var passwordService: PasswordService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `given 2 users from env config findAllNotDeleted returns these 2 users`() {
        val repository = getRepositoryWithUsers(USER1, USER2)

        val users = repository.findAllNotDeleted()

        assertEquals(2, users.size)
        assertTrue(users.any { it.username == "user" && it.passwordHash == "hash" && it.role == "USER" })
        assertTrue(users.any { it.username == "admin" && it.passwordHash == "hash" && it.role == "ADMIN" })
    }

    @Test
    fun `given hash of username as id of user findById returns that user`() {
        val repository = getRepositoryWithUsers(USER1, USER2)
        val user = repository.findById("user".hashCode())
        assertNotNull(user)
        assertTrue(user.username == "user")
    }

    @Test
    fun `given 'user' as a username of user findByUsername returns user with 'user' username`() {
        val repository = getRepositoryWithUsers(USER1, USER2)
        val user = repository.findByUsername("user")
        assertNotNull(user)
        assertTrue(user.username == "user")
    }

    private fun getRepositoryWithUsers(vararg users: String): EnvUserRepository {
        whenever(passwordService.hashPassword(any())).thenAnswer { "hash" }
        val repository = EnvUserRepository(MapApplicationConfig().apply {
            put("drill.users", users.toList())
        }, passwordService)
        return repository
    }
}