package io.mikoshift.natsu.data.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryUrlValidatorTest {

    @Test
    fun validateDownloadUrl_acceptsAllowedGitHubHosts() {
        val urls = listOf(
            "https://github.com/user/repo/releases/download/v1/dict.zip",
            "https://raw.githubusercontent.com/user/repo/main/dict.zip",
            "https://objects.githubusercontent.com/github-production-release-asset/abc",
            "https://codeload.github.com/user/repo/zip/refs/heads/main",
        )
        urls.forEach { DictionaryUrlValidator.validateDownloadUrl(it) }
    }

    @Test
    fun validateDownloadUrl_rejectsHttp() {
        val error = runCatching {
            DictionaryUrlValidator.validateDownloadUrl(
                "http://github.com/user/repo/releases/download/v1/dict.zip",
            )
        }.exceptionOrNull()
        assertEquals(true, error?.message?.contains("HTTPS") == true)
    }

    @Test
    fun validateDownloadUrl_rejectsDisallowedHost() {
        val error = runCatching {
            DictionaryUrlValidator.validateDownloadUrl("https://evil.example.com/dict.zip")
        }.exceptionOrNull()
        assertEquals(true, error?.message?.contains("not allowed") == true)
    }

    @Test
    fun validateDownloadUrl_rejectsEmptyUrl() {
        val error = runCatching {
            DictionaryUrlValidator.validateDownloadUrl("   ")
        }.exceptionOrNull()
        assertEquals(true, error?.message?.contains("empty") == true)
    }

    @Test
    fun computeMaxBytes_usesSizeHintWithHeadroom() {
        val maxBytes = DictionaryUrlValidator.computeMaxBytes(10)
        assertEquals((10 * 1.5 * 1024 * 1024).toLong(), maxBytes)
    }

    @Test
    fun computeMaxBytes_fallsBackToDefaultWhenHintMissing() {
        assertEquals(
            DictionaryUrlValidator.MAX_DOWNLOAD_BYTES_DEFAULT,
            DictionaryUrlValidator.computeMaxBytes(0),
        )
    }
}
