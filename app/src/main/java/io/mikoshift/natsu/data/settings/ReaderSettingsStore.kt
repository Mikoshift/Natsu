package io.mikoshift.natsu.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.mikoshift.natsu.domain.model.FuriganaMode
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.domain.model.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.readerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reader_settings",
)

class ReaderSettingsStore(
    private val context: Context,
) {
    val settings: Flow<ReaderSettings> = context.readerSettingsDataStore.data.map { preferences ->
        preferences.toReaderSettings()
    }

    suspend fun readSettingsSnapshot(): ReaderSettings =
        context.readerSettingsDataStore.data.first().toReaderSettings()

    suspend fun readSyncMetadata(): ReaderSettingsSyncMetadata {
        val preferences = context.readerSettingsDataStore.data.first()
        return ReaderSettingsSyncMetadata(
            updatedAtMs = preferences[KEY_UPDATED_AT_MS] ?: 0L,
            syncDirty = preferences[KEY_SYNC_DIRTY] ?: false,
        )
    }

    suspend fun updateFontSize(fontSizeSp: Float) {
        markDirty { it[KEY_FONT_SIZE] = fontSizeSp }
    }

    suspend fun updateLineSpacing(lineSpacingMultiplier: Float) {
        markDirty { it[KEY_LINE_SPACING] = lineSpacingMultiplier }
    }

    suspend fun updateTheme(theme: ReaderTheme) {
        markDirty { it[KEY_THEME] = theme.name }
    }

    suspend fun updateFuriganaMode(furiganaMode: FuriganaMode) {
        markDirty { it[KEY_FURIGANA_MODE] = furiganaMode.name }
    }

    suspend fun applyRemoteSettings(
        fontSizeSp: Float,
        lineSpacingMultiplier: Float,
        theme: String,
        furiganaMode: String,
        updatedAtMs: Long,
    ) {
        context.readerSettingsDataStore.edit { preferences ->
            preferences[KEY_FONT_SIZE] = fontSizeSp
            preferences[KEY_LINE_SPACING] = lineSpacingMultiplier
            preferences[KEY_THEME] = theme
            preferences[KEY_FURIGANA_MODE] = furiganaMode
            preferences[KEY_UPDATED_AT_MS] = updatedAtMs
            preferences[KEY_SYNC_DIRTY] = false
        }
    }

    suspend fun applyRemoteMetadata(updatedAtMs: Long, syncDirty: Boolean) {
        context.readerSettingsDataStore.edit { preferences ->
            preferences[KEY_UPDATED_AT_MS] = updatedAtMs
            preferences[KEY_SYNC_DIRTY] = syncDirty
        }
    }

    private suspend fun markDirty(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        val now = System.currentTimeMillis()
        context.readerSettingsDataStore.edit { preferences ->
            block(preferences)
            preferences[KEY_UPDATED_AT_MS] = now
            preferences[KEY_SYNC_DIRTY] = true
        }
    }

    private fun Preferences.toReaderSettings(): ReaderSettings = ReaderSettings(
        fontSizeSp = this[KEY_FONT_SIZE] ?: ReaderSettings().fontSizeSp,
        lineSpacingMultiplier = this[KEY_LINE_SPACING] ?: ReaderSettings().lineSpacingMultiplier,
        theme = this[KEY_THEME]?.let { name ->
            runCatching { ReaderTheme.valueOf(name) }.getOrDefault(ReaderTheme.LIGHT)
        } ?: ReaderTheme.LIGHT,
        furiganaMode = this[KEY_FURIGANA_MODE]?.let { name ->
            runCatching { FuriganaMode.valueOf(name) }.getOrDefault(FuriganaMode.OFF)
        } ?: FuriganaMode.OFF,
    )

    private companion object {
        val KEY_FONT_SIZE = floatPreferencesKey("font_size_sp")
        val KEY_LINE_SPACING = floatPreferencesKey("line_spacing")
        val KEY_THEME = stringPreferencesKey("reader_theme")
        val KEY_FURIGANA_MODE = stringPreferencesKey("furigana_mode")
        val KEY_UPDATED_AT_MS = longPreferencesKey("updated_at_ms")
        val KEY_SYNC_DIRTY = booleanPreferencesKey("sync_dirty")
    }
}
