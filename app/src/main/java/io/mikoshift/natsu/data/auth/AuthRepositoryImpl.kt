package io.mikoshift.natsu.data.auth

import io.mikoshift.natsu.data.remote.ApiException
import io.mikoshift.natsu.data.remote.NatsuApiClient
import io.mikoshift.natsu.data.remote.dto.UserDto
import io.mikoshift.natsu.domain.model.User
import io.mikoshift.natsu.domain.repository.AuthRepository
import io.mikoshift.natsu.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow

class AuthRepositoryImpl(
    private val apiClient: NatsuApiClient,
    private val sessionStore: SessionStore,
    private val syncRepository: SyncRepository,
) : AuthRepository {
    override val authState: Flow<io.mikoshift.natsu.domain.model.AuthState> = sessionStore.authState

    override suspend fun hydrateSession() {
        sessionStore.hydrateFromDisk()
    }

    override suspend fun login(email: String, password: String): Result<User> = runCatching {
        val response = apiClient.login(
            email = email.trim(),
            password = password,
            deviceName = DEVICE_NAME,
        )
        val user = response.user.toDomain()
        sessionStore.saveSession(response.token, user)
        syncRepository.scheduleSync()
        user
    }.recoverCatching { error ->
        if (error is ApiException && error.code == 401) {
            sessionStore.clearSession()
        }
        throw error
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Result<User> = runCatching {
        val response = apiClient.register(
            name = name.trim(),
            email = email.trim(),
            password = password,
            deviceName = DEVICE_NAME,
        )
        val user = response.user.toDomain()
        sessionStore.saveSession(response.token, user)
        syncRepository.scheduleSync()
        user
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        apiClient.logout()
        sessionStore.clearSession()
    }

    override suspend fun deleteAccount(password: String): Result<Unit> = runCatching {
        apiClient.deleteAccount(password)
        sessionStore.clearSession()
    }

    private fun UserDto.toDomain(): User = User(
        id = id,
        name = name,
        email = email,
    )

    private companion object {
        const val DEVICE_NAME = "android"
    }
}
