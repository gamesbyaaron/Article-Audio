package app.articleaudio

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

/** What a WAV file's header says about the sound that follows it. */
class WavInfo(val sampleRate: Int, val channels: Int, val bitsPerSample: Int, val dataBytes: Long)

/** Reads the WAV files the speech engine writes. Pure Kotlin, unit tested. */
object Wav {
    /** Reads up to the start of the sound data and leaves [input] positioned there. */
    fun readHeader(input: InputStream): WavInfo {
        val data = DataInputStream(input)
        fun tag(): String = ByteArray(4).also { data.readFully(it) }.toString(Charsets.US_ASCII)
        fun u16(): Int = data.readUnsignedByte() or (data.readUnsignedByte() shl 8)
        fun u32(): Long = u16().toLong() or (u16().toLong() shl 16)
        fun skip(count: Long) {
            var left = count
            while (left > 0) {
                val skipped = data.skipBytes(minOf(left, 8192L).toInt())
                if (skipped <= 0) throw IOException("WAV file ended early")
                left -= skipped
            }
        }

        if (tag() != "RIFF") throw IOException("Not a WAV file")
        u32()
        if (tag() != "WAVE") throw IOException("Not a WAV file")

        var sampleRate = 0
        var channels = 0
        var bits = 0
        while (true) {
            val id = tag()
            val size = u32()
            when (id) {
                "fmt " -> {
                    val encoding = u16()
                    channels = u16()
                    sampleRate = u32().toInt()
                    u32() // byte rate
                    u16() // block align
                    bits = u16()
                    skip(size - 16)
                    if (encoding != 1) throw IOException("Unsupported WAV encoding $encoding (need plain PCM)")
                }
                "data" -> {
                    if (sampleRate == 0) throw IOException("WAV file has no format section")
                    return WavInfo(sampleRate, channels, bits, size)
                }
                else -> skip(size + (size and 1))
            }
        }
    }

    /** A safe file name for an article title, without the extension. */
    fun fileName(title: String): String {
        val cleaned = title.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").trim().trim('.')
        return cleaned.take(80).trim().ifEmpty { "article" }
    }
}
