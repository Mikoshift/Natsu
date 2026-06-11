package io.mikoshift.natsu.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.mikoshift.natsu.domain.model.TextToken

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TokenizedParagraph(
    tokens: List<TextToken>,
    onWordClick: (TextToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        tokens.forEach { token ->
            if (token.isClickable) {
                Text(
                    text = token.surface,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.8f,
                    ),
                    modifier = Modifier.clickable { onWordClick(token) },
                )
            } else {
                Text(
                    text = token.surface,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.8f,
                        fontWeight = if (token.surface.trim().isEmpty()) FontWeight.Normal else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}
