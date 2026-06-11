package io.mikoshift.natsu.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.mikoshift.natsu.domain.model.FuriganaMode
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.domain.model.TextToken

private const val WORD_ANNOTATION_TAG = "word"
private const val FURIGANA_FONT_SCALE = 0.55f
private const val FURIGANA_SLOT_RATIO = 0.65f

@Composable
fun TokenizedParagraph(
    tokens: List<TextToken>,
    settings: ReaderSettings,
    onWordClick: (TextToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (settings.furiganaMode) {
        FuriganaMode.OFF -> PlainTokenizedParagraph(
            tokens = tokens,
            settings = settings,
            onWordClick = onWordClick,
            modifier = modifier,
        )
        FuriganaMode.ALWAYS -> FuriganaTokenizedParagraph(
            tokens = tokens,
            settings = settings,
            onWordClick = onWordClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun PlainTokenizedParagraph(
    tokens: List<TextToken>,
    settings: ReaderSettings,
    onWordClick: (TextToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textStyle = readerTextStyle(settings)
    val annotatedString = remember(tokens) {
        buildAnnotatedString {
            tokens.forEachIndexed { index, token ->
                if (token.isClickable) {
                    pushStringAnnotation(tag = WORD_ANNOTATION_TAG, annotation = index.toString())
                    append(token.surface)
                    pop()
                } else {
                    append(token.surface)
                }
            }
        }
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedString,
        style = textStyle,
        onTextLayout = { textLayoutResult = it },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(tokens, onWordClick) {
                detectTapGestures { offset ->
                    val layout = textLayoutResult ?: return@detectTapGestures
                    val position = layout.getOffsetForPosition(offset)
                    annotatedString
                        .getStringAnnotations(
                            tag = WORD_ANNOTATION_TAG,
                            start = position,
                            end = position,
                        )
                        .firstOrNull()
                        ?.let { annotation ->
                            tokens.getOrNull(annotation.item.toInt())?.let(onWordClick)
                        }
                }
            },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FuriganaTokenizedParagraph(
    tokens: List<TextToken>,
    settings: ReaderSettings,
    onWordClick: (TextToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textStyle = readerTextStyle(settings)
    val furiganaStyle = furiganaTextStyle(settings)
    val density = LocalDensity.current
    val furiganaSlotHeight = with(density) {
        (settings.fontSizeSp * FURIGANA_SLOT_RATIO).sp.toDp()
    }

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        tokens.forEach { token ->
            FuriganaToken(
                token = token,
                textStyle = textStyle,
                furiganaStyle = furiganaStyle,
                furiganaSlotHeight = furiganaSlotHeight,
                onClick = { onWordClick(token) },
            )
        }
    }
}

@Composable
private fun FuriganaToken(
    token: TextToken,
    textStyle: TextStyle,
    furiganaStyle: TextStyle,
    furiganaSlotHeight: Dp,
    onClick: () -> Unit,
) {
    val showRuby = shouldShowFurigana(token)
    val clickModifier = if (token.isClickable) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = clickModifier,
    ) {
        Box(
            modifier = Modifier.heightIn(min = furiganaSlotHeight),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (showRuby) {
                Text(
                    text = furiganaReading(token),
                    style = furiganaStyle,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            text = token.surface,
            style = textStyle,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun readerTextStyle(settings: ReaderSettings): TextStyle {
    val fontSize = settings.fontSizeSp.sp
    val lineHeightSp = if (settings.furiganaMode == FuriganaMode.ALWAYS) {
        settings.fontSizeSp * (FURIGANA_SLOT_RATIO + settings.lineSpacingMultiplier)
    } else {
        settings.fontSizeSp * settings.lineSpacingMultiplier
    }
    return MaterialTheme.typography.bodyLarge.copy(
        fontSize = fontSize,
        lineHeight = lineHeightSp.sp,
    )
}

@Composable
private fun furiganaTextStyle(settings: ReaderSettings): TextStyle =
    MaterialTheme.typography.bodySmall.copy(
        fontSize = (settings.fontSizeSp * FURIGANA_FONT_SCALE).sp,
        lineHeight = (settings.fontSizeSp * FURIGANA_FONT_SCALE).sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
