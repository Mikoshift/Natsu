package io.mikoshift.natsu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.domain.model.ReaderTheme

private val ReaderLightBackground = Color(0xFFFFFFFF)
private val ReaderLightOnBackground = Color(0xFF1C1B1F)

private val ReaderDarkBackground = Color(0xFF121212)
private val ReaderDarkOnBackground = Color(0xFFE6E1E5)

private val ReaderSepiaBackground = Color(0xFFF4ECD8)
private val ReaderSepiaOnBackground = Color(0xFF5B4636)

fun readerColorScheme(theme: ReaderTheme) = when (theme) {
    ReaderTheme.LIGHT -> lightColorScheme(
        background = ReaderLightBackground,
        onBackground = ReaderLightOnBackground,
        surface = ReaderLightBackground,
        onSurface = ReaderLightOnBackground,
    )
    ReaderTheme.DARK -> darkColorScheme(
        background = ReaderDarkBackground,
        onBackground = ReaderDarkOnBackground,
        surface = ReaderDarkBackground,
        onSurface = ReaderDarkOnBackground,
    )
    ReaderTheme.SEPIA -> lightColorScheme(
        background = ReaderSepiaBackground,
        onBackground = ReaderSepiaOnBackground,
        surface = ReaderSepiaBackground,
        onSurface = ReaderSepiaOnBackground,
    )
}

@Composable
fun ReaderThemeWrapper(
    settings: ReaderSettings,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = readerColorScheme(settings.theme),
        typography = Typography,
        content = content,
    )
}
