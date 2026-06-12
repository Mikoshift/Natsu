package io.mikoshift.natsu.data.book.import

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class DecodedText(
    val text: String,
    val charset: Charset,
)

object TextCharsetDecoder {
    private val SHIFT_JIS: Charset = Charset.forName("Shift_JIS")
    private val EUC_JP: Charset = Charset.forName("EUC-JP")

    private val DETECTION_ORDER = listOf(
        StandardCharsets.UTF_8,
        SHIFT_JIS,
        EUC_JP,
    )

    fun decode(bytes: ByteArray): DecodedText {
        if (bytes.isEmpty()) {
            return DecodedText(text = "", charset = StandardCharsets.UTF_8)
        }

        val payload = stripUtf8Bom(bytes)
        val decoded = DETECTION_ORDER.firstNotNullOfOrNull { charset ->
            decodeStrict(payload, charset)?.let { text ->
                DecodedText(text = normalizeLineEndings(text), charset = charset)
            }
        } ?: throw UnsupportedTextEncodingException()

        return decoded
    }

    private fun stripUtf8Bom(bytes: ByteArray): ByteArray {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return bytes.copyOfRange(3, bytes.size)
        }
        return bytes
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? {
        return try {
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val byteBuffer = ByteBuffer.wrap(bytes)
            val charBuffer = decoder.decode(byteBuffer)
            if (byteBuffer.hasRemaining()) return null
            val chars = CharArray(charBuffer.remaining())
            charBuffer.get(chars)
            String(chars)
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun normalizeLineEndings(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n')
}

class UnsupportedTextEncodingException :
    BookImportException("Unsupported text encoding")
