package io.mikoshift.natsu.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import io.mikoshift.natsu.domain.model.ReaderSettings

private val SearchHighlightColor = Color(0xFFFFD54F)
private val SearchHighlightTextColor = Color(0xFF3E2723)

@Composable
fun ReaderHeading(
    text: String,
    level: Int,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
    highlightRange: IntRange? = null,
) {
    val typography = when (level.coerceIn(1, 6)) {
        1 -> MaterialTheme.typography.headlineLarge
        2 -> MaterialTheme.typography.headlineMedium
        3 -> MaterialTheme.typography.headlineSmall
        4 -> MaterialTheme.typography.titleLarge
        5 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    val style = typography.copy(
        fontSize = (settings.fontSizeSp * headingScale(level)).sp,
        lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier * headingScale(level)).sp,
        fontWeight = FontWeight.SemiBold,
    )
    val annotatedText = remember(text, highlightRange) {
        buildAnnotatedString {
            if (highlightRange == null) {
                append(text)
            } else {
                val highlightStyle = SpanStyle(
                    background = SearchHighlightColor,
                    color = SearchHighlightTextColor,
                )
                val start = highlightRange.first.coerceIn(0, text.length)
                val end = (highlightRange.last + 1).coerceIn(0, text.length)
                if (start > 0) append(text.substring(0, start))
                withStyle(highlightStyle) {
                    append(text.substring(start, end))
                }
                if (end < text.length) append(text.substring(end))
            }
        }
    }

    Text(
        text = annotatedText,
        style = style,
        modifier = modifier,
    )
}

private fun headingScale(level: Int): Float =
    when (level.coerceIn(1, 6)) {
        1 -> 1.5f
        2 -> 1.35f
        3 -> 1.2f
        4 -> 1.1f
        5 -> 1.0f
        else -> 0.95f
    }
