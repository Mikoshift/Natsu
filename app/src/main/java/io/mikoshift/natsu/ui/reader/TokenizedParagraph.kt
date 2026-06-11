package io.mikoshift.natsu.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.domain.model.TextToken

@Composable
fun TokenizedParagraph(
    tokens: List<TextToken>,
    settings: ReaderSettings,
    onWordClick: (TextToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = settings.fontSizeSp.sp,
        lineHeight = settings.fontSizeSp.sp * settings.lineSpacingMultiplier,
    )
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

private const val WORD_ANNOTATION_TAG = "word"
