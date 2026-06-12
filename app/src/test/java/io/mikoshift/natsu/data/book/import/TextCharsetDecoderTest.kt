package io.mikoshift.natsu.data.book.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class TextCharsetDecoderTest {

    @Test
    fun decode_utf8JapaneseText() {
        val text = "吾輩は猫である。名前はまだ無い。"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)

        val decoded = TextCharsetDecoder.decode(bytes)

        assertEquals(StandardCharsets.UTF_8, decoded.charset)
        assertEquals(text, decoded.text)
    }

    @Test
    fun decode_utf8WithBom() {
        val text = "Hello"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            text.toByteArray(StandardCharsets.UTF_8)

        val decoded = TextCharsetDecoder.decode(bytes)

        assertEquals(StandardCharsets.UTF_8, decoded.charset)
        assertEquals(text, decoded.text)
    }

    @Test
    fun decode_shiftJisJapaneseText() {
        val text = "吾輩は猫である。名前はまだ無い。"
        val bytes = text.toByteArray(Charset.forName("Shift_JIS"))

        val decoded = TextCharsetDecoder.decode(bytes)

        assertEquals(Charset.forName("Shift_JIS"), decoded.charset)
        assertEquals(text, decoded.text)
    }

    @Test
    fun decode_eucJpJapaneseText() {
        val text = "吾輩は猫である。名前はまだ無い。"
        val bytes = text.toByteArray(Charset.forName("EUC-JP"))

        val decoded = TextCharsetDecoder.decode(bytes)

        assertEquals(Charset.forName("EUC-JP"), decoded.charset)
        assertEquals(text, decoded.text)
    }

    @Test
    fun decode_normalizesLineEndings() {
        val bytes = "Line one\r\nLine two\rLine three\n".toByteArray(StandardCharsets.UTF_8)

        val decoded = TextCharsetDecoder.decode(bytes)

        assertEquals("Line one\nLine two\nLine three\n", decoded.text)
    }

    @Test
    fun decode_emptyFile_returnsEmptyString() {
        val decoded = TextCharsetDecoder.decode(byteArrayOf())

        assertEquals("", decoded.text)
        assertEquals(StandardCharsets.UTF_8, decoded.charset)
    }

    @Test
    fun decode_invalidBytes_throwsUnsupportedEncoding() {
        assertThrows(UnsupportedTextEncodingException::class.java) {
            TextCharsetDecoder.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00))
        }
    }
}
