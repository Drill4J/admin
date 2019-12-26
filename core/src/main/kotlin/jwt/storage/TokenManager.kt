package com.epam.drill.admin.jwt.storage

import com.epam.kodux.*
import kotlinx.serialization.*
import org.kodein.di.*
import org.kodein.di.generic.*

class TokenManager(override val kodein: Kodein) : KodeinAware {
    private val store: StoreManager by instance()
    private val tokenStorage = store.agentStore("tokens")

    suspend fun addToken(token: String) {
        tokenStorage.store(RefreshToken(token))
    }

    suspend fun containToken(token: String) =
        tokenStorage.findById<RefreshToken>(token) != null

    suspend fun deleteToken(token: String) {
        tokenStorage.deleteById<RefreshToken>(token)
    }

    suspend fun getAll() =
        tokenStorage.getAll<RefreshToken>()

}

@Serializable
data class RefreshToken(
    @Id
    val token: String
)
