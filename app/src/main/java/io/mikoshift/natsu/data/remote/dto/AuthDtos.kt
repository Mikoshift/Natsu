package io.mikoshift.natsu.data.remote.dto

data class AuthResponseDto(
    val token: String,
    val user: UserDto,
)

data class UserDto(
    val id: Long,
    val name: String,
    val email: String,
)

data class UserEnvelopeDto(
    val data: UserDto,
)

data class MessageResponseDto(
    val message: String,
)
