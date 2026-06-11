package io.mikoshift.natsu.data.dictionary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DictionaryDownloadManager(
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun download(
        url: String,
        destination: File,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            destination.parentFile?.mkdirs()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: error("Empty response body")
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                onProgress(downloaded.toFloat() / totalBytes.toFloat())
                            }
                        }
                    }
                }
                onProgress(1f)
            }
        }
    }
}
