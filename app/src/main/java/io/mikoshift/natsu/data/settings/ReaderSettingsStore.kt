package io.mikoshift.natsu.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.mikoshift.natsu.domain.model.FuriganaMode
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.domain.model.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reader_settings",
)

class ReaderSettingsStore(
    private val context: Context,
) {
    val settings: Flow<ReaderSettings> = context.readerSettingsDataStore.data.map { preferences ->
        ReaderSettings(
            fontSizeSp = preferences[KEY_FONT_SIZE] ?: ReaderSettings().fontSizeSp,
            lineSpacingMultiplier = preferences[KEY_LINE_SPACING] ?: ReaderSettings().lineSpacingMultiplier,
            theme = preferences[KEY_THEME]?.let { name ->
                runCatching { ReaderTheme.valueOf(name) }.getOrDefault(ReaderTheme.LIGHT)
            } ?: ReaderTheme.LIGHT,
            furiganaMode = preferences[KEY_FURIGANA_MODE]?.let { name ->
                runCatching { FuriganaMode.valueOf(name) }.getOrDefault(FuriganaMode.OFF)
            } ?: FuriganaMode.OFF,
        )
    }

    suspend fun updateFontSize(fontSizeSp: Float) {
        context.readerSettingsDataStore.edit { preferences ->
            preferences[KEY_FONT_SIZE] = fontSizeSp
        }
    }

    suspend fun updateLineSpacing(lineSpacingMultiplier: Float) {
        context.readerSettingsDataStore.edit { preferences ->
            preferences[KEY_LINE_SPACING] = lineSpacingMultiplier
        }
    }

    suspend fun updateTheme(theme: ReaderTheme) {
        context.readerSettingsDataStore.edit { preferences ->
            preferences[KEY_THEME] = theme.name
        }
    }

    suspend fun updateFuriganaMode(furiganaMode: FuriganaMode) {
        context.readerSettingsDataStore.edit { preferences ->
            preferences[KEY_FURIGANA_MODE] = furiganaMode.name
        }
    }

    private companion object {
        val KEY_FONT_SIZE = floatPreferencesKey("font_size_sp")
        val KEY_LINE_SPACING = floatPreferencesKey("line_spacing")
        val KEY_THEME = stringPreferencesKey("reader_theme")
        val KEY_FURIGANA_MODE = stringPreferencesKey("furigana_mode")
    }
}
