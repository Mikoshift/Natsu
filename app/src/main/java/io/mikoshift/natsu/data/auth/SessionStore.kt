package io.mikoshift.natsu.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.mikoshift.natsu.domain.model.AuthState
import io.mikoshift.natsu.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicReference

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "natsu_session",
)

class SessionStore(
    context: Context,
) {
    private val dataStore = context.applicationContext.sessionDataStore
    private val cachedToken = AtomicReference<String?>(null)

    val authState: Flow<AuthState> = dataStore.data.map { preferences ->
        val token = preferences[KEY_TOKEN]
        val userId = preferences[KEY_USER_ID]?.toLongOrNull()
        val userName = preferences[KEY_USER_NAME]
        val userEmail = preferences[KEY_USER_EMAIL]
        if (token.isNullOrBlank() || userId == null || userName.isNullOrBlank() || userEmail.isNullOrBlank()) {
            AuthState.Guest
        } else {
            AuthState.Authenticated(
                User(
                    id = userId,
                    name = userName,
                    email = userEmail,
                ),
            )
        }
    }

    val lastSyncAtMs: Flow<Long> = dataStore.data.map { preferences ->
        preferences[KEY_LAST_SYNC_AT_MS] ?: 0L
    }

    fun currentToken(): String? = cachedToken.get()

    suspend fun hydrateFromDisk() {
        cachedToken.set(dataStore.data.first()[KEY_TOKEN])
    }

    suspend fun saveSession(token: String, user: User) {
        cachedToken.set(token)
        dataStore.edit { preferences ->
            preferences[KEY_TOKEN] = token
            preferences[KEY_USER_ID] = user.id.toString()
            preferences[KEY_USER_NAME] = user.name
            preferences[KEY_USER_EMAIL] = user.email
        }
    }

    suspend fun updateUser(user: User) {
        dataStore.edit { preferences ->
            preferences[KEY_USER_ID] = user.id.toString()
            preferences[KEY_USER_NAME] = user.name
            preferences[KEY_USER_EMAIL] = user.email
        }
    }

    suspend fun setLastSyncAtMs(timestampMs: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_SYNC_AT_MS] = timestampMs
        }
    }

    suspend fun clearSession() {
        cachedToken.set(null)
        dataStore.edit { preferences ->
            preferences.remove(KEY_TOKEN)
            preferences.remove(KEY_USER_ID)
            preferences.remove(KEY_USER_NAME)
            preferences.remove(KEY_USER_EMAIL)
        }
    }

    suspend fun readLastSyncAtMs(): Long = dataStore.data.first()[KEY_LAST_SYNC_AT_MS] ?: 0L

    private companion object {
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        val KEY_LAST_SYNC_AT_MS = longPreferencesKey("last_sync_at_ms")
    }
}
