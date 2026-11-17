package app.articleaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class WavTest {
    private fun le(value: Int, bytes: Int) = ByteArray(bytes) { (value shr (8 * it)).toByte() }

    private fun wav(encoding: Int = 1, extraChunk: Boolean = false, data: ByteArray = byteArrayOf(1, 2, 3, 4)): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray()); out.write(le(0, 4)); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); out.write(le(16, 4))
        out.write(le(encoding, 2)); out.write(le(1, 2)); out.write(le(24000, 4))
        out.write(le(48000, 4)); out.write(le(2, 2)); out.write(le(16, 2))
        if (extraChunk) {
            out.write("LIST".toByteArray()); out.write(le(3, 4)); out.write(byteArrayOf(9, 9, 9, 0))
        }
        out.write("data".toByteArray()); out.write(le(data.size, 4)); out.write(data)
        return out.toByteArray()
    }

    @Test
    fun readsFormatAndStopsAtTheSound() {
        val input = ByteArrayInputStream(wav())
        val info = Wav.readHeader(input)
        assertEquals(24000, info.sampleRate)
        assertEquals(1, info.channels)
        assertEquals(16, info.bitsPerSample)
        assertEquals(4L, info.dataBytes)
        assertEquals(listOf<Byte>(1, 2, 3, 4), input.readBytes().toList())
    }

    @Test
    fun skipsOtherSectionsIncludingOddSizedOnes() {
        val input = ByteArrayInputStream(wav(extraChunk = true))
        Wav.readHeader(input)
        assertEquals(listOf<Byte>(1, 2, 3, 4), input.readBytes().toList())
    }

    @Test
    fun rejectsCompressedSound() {
        assertThrows(IOException::class.java) { Wav.readHeader(ByteArrayInputStream(wav(encoding = 3))) }
    }

    @Test
    fun rejectsOtherFiles() {
        assertThrows(IOException::class.java) { Wav.readHeader(ByteArrayInputStream("not a wav file at all".toByteArray())) }
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("Mass–energy equivalence", Wav.fileName("Mass–energy equivalence"))
        assertEquals("AC_DC", Wav.fileName("AC/DC"))
        assertEquals("What_ Why_", Wav.fileName("What? Why?"))
        assertEquals("article", Wav.fileName("..."))
        assertEquals(80, Wav.fileName("x".repeat(200)).length)
    }
}
