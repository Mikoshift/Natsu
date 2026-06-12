package io.mikoshift.natsu.ui.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    if (source.startsWith("http://") || source.startsWith("https://")) {
        ReaderImagePlaceholder(alt = alt, modifier = modifier)
        return
    }

    val localFile = resolveLocalImageFile(bookStoragePath, source)
    if (localFile == null) {
        ReaderImagePlaceholder(alt = alt, modifier = modifier)
        return
    }

    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(localFile)
            .crossfade(true)
            .build(),
        contentDescription = alt,
        contentScale = ContentScale.FillWidth,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun ReaderImagePlaceholder(
    alt: String?,
    modifier: Modifier = Modifier,
) {
    if (alt.isNullOrBlank()) return
    Text(
        text = alt,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

internal fun resolveLocalImageFile(bookStoragePath: String, source: String): File? {
    if (source.contains("..") || source.startsWith("/")) return null
    return runCatching {
        val bookRoot = File(bookStoragePath).canonicalFile
        val imageFile = File(bookRoot, source).canonicalFile
        if (!imageFile.isUnderDirectory(bookRoot) || !imageFile.isFile) {
            null
        } else {
            imageFile
        }
    }.getOrNull()
}

private fun File.isUnderDirectory(root: File): Boolean {
    val rootPath = root.path
    val filePath = path
    return filePath == rootPath || filePath.startsWith("$rootPath${File.separator}")
}
