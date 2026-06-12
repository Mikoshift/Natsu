package io.mikoshift.natsu.data.dictionary

import java.net.URI

object DictionaryUrlValidator {
    val ALLOWED_HOSTS: Set<String> = setOf(
        "github.com",
        "raw.githubusercontent.com",
        "objects.githubusercontent.com",
        "codeload.github.com",
    )

    const val MAX_DOWNLOAD_BYTES_DEFAULT: Long = 100L * 1024 * 1024

    fun validateDownloadUrl(url: String) {
        require(url.isNotBlank()) { "Download URL must not be empty" }
        val uri = URI(url)
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Download URL must use HTTPS: $url"
        }
        val host = uri.host?.lowercase()
            ?: error("Download URL must have a host: $url")
        require(host in ALLOWED_HOSTS) {
            "Download URL host not allowed: $host"
        }
    }

    fun computeMaxBytes(sizeHintMb: Int): Long =
        if (sizeHintMb > 0) {
            (sizeHintMb * 1.5 * 1024 * 1024).toLong()
        } else {
            MAX_DOWNLOAD_BYTES_DEFAULT
        }
}
