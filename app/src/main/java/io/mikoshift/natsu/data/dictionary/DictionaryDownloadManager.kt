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
        maxBytes: Long = DictionaryUrlValidator.MAX_DOWNLOAD_BYTES_DEFAULT,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            DictionaryUrlValidator.validateDownloadUrl(url)
            require(maxBytes > 0) { "maxBytes must be positive" }

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
                        var zipMagicVerified = false
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            if (!zipMagicVerified) {
                                if (read < 2 ||
                                    buffer[0] != ZIP_MAGIC_BYTE_0 ||
                                    buffer[1] != ZIP_MAGIC_BYTE_1
                                ) {
                                    error("Download is not a valid ZIP archive")
                                }
                                zipMagicVerified = true
                            }
                            downloaded += read
                            if (downloaded > maxBytes) {
                                error("Download exceeds maximum size limit ($maxBytes bytes)")
                            }
                            output.write(buffer, 0, read)
                            if (totalBytes > 0) {
                                onProgress(downloaded.toFloat() / totalBytes.toFloat())
                            }
                        }
                        if (!zipMagicVerified) {
                            error("Download is not a valid ZIP archive")
                        }
                    }
                }
                onProgress(1f)
            }
        }
    }

    companion object {
        private const val ZIP_MAGIC_BYTE_0: Byte = 0x50
        private const val ZIP_MAGIC_BYTE_1: Byte = 0x4B
    }
}
