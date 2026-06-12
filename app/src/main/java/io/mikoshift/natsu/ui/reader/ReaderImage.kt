package io.mikoshift.natsu.ui.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@Composable
fun ReaderImage(
    source: String,
    alt: String?,
    bookStoragePath: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val model = if (source.startsWith("http://") || source.startsWith("https://")) {
        source
    } else {
        File(bookStoragePath, source)
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .build(),
        contentDescription = alt,
        contentScale = ContentScale.FillWidth,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}
