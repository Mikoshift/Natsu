package io.mikoshift.natsu.domain.model

sealed interface AuthState {
    data object Guest : AuthState

    data class Authenticated(
        val user: User,
    ) : AuthState
}
