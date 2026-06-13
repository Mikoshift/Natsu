package io.mikoshift.natsu.domain.repository

import io.mikoshift.natsu.domain.model.AuthState
import io.mikoshift.natsu.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    suspend fun hydrateSession()

    suspend fun login(email: String, password: String): Result<User>

    suspend fun register(name: String, email: String, password: String): Result<User>

    suspend fun logout(): Result<Unit>

    suspend fun deleteAccount(password: String): Result<Unit>
}
