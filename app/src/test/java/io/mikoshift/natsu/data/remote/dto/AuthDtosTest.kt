package io.mikoshift.natsu.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthDtosTest {
    private val gson = Gson()

    @Test
    fun loginResponse_parsesTokenAndUser() {
        val json = """
            {
              "token": "abc123",
              "user": {
                "id": 1,
                "name": "Test User",
                "email": "test@example.com"
              }
            }
        """.trimIndent()

        val response = gson.fromJson(json, AuthResponseDto::class.java)

        assertEquals("abc123", response.token)
        assertEquals("Test User", response.user.name)
        assertEquals("test@example.com", response.user.email)
    }

    @Test
    fun userEnvelope_parsesWrappedUser() {
        val json = """
            {
              "data": {
                "id": 2,
                "name": "Wrapped",
                "email": "wrapped@example.com"
              }
            }
        """.trimIndent()

        val response = gson.fromJson(json, UserEnvelopeDto::class.java)

        assertEquals("Wrapped", response.data.name)
        assertEquals(2L, response.data.id)
    }
}
