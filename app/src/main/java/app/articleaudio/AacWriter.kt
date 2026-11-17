package app.articleaudio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException

/**
 * Encodes 16-bit PCM sound to AAC in an .m4a file using Android's built-in encoder. Sound is
 * fed in small pieces and written out as it goes, so a long article is never held in memory.
 */
class AacWriter(file: File, private val sampleRate: Int, private val channels: Int) {
    private val frameBytes = 2 * channels
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MIME)
    private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var muxerStarted = false
    private var framesWritten = 0L

    init {
        val format = MediaFormat.createAudioFormat(MIME, sampleRate, channels)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64_000 * channels)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    /** Milliseconds of sound written so far. */
    val millis: Long get() = framesWritten * 1000 / sampleRate

    /** Seconds of sound written so far. */
    val seconds: Long get() = framesWritten / sampleRate

    /** Adds the first [length] bytes of [pcm]; a trailing half sample is ignored. */
    fun write(pcm: ByteArray, length: Int) {
        val usable = length - length % frameBytes
        var offset = 0
        while (offset < usable) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) {
                val buffer = codec.getInputBuffer(index) ?: throw IOException("Encoder gave no input buffer")
                buffer.clear()
                var count = minOf(buffer.remaining(), usable - offset)
                count -= count % frameBytes
                buffer.put(pcm, offset, count)
                codec.queueInputBuffer(index, 0, count, timeUs(), 0)
                offset += count
                framesWritten += count / frameBytes
            }
            drain(untilEnd = false)
        }
    }

    fun silence(milliseconds: Int) {
        val bytes = ByteArray(sampleRate * milliseconds / 1000 * frameBytes)
        write(bytes, bytes.size)
    }

    /** Flushes the encoder and completes the file. */
    fun finish() {
        var tries = 0
        while (true) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) {
                codec.queueInputBuffer(index, 0, 0, timeUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                break
            }
            drain(untilEnd = false)
            if (++tries > 500) throw IOException("Encoder did not accept the end of the sound")
        }
        drain(untilEnd = true)
        if (!muxerStarted) throw IOException("No sound was encoded")
        muxer.stop()
    }

    /** Frees the encoder. Safe to call after a failure or after [finish]. */
    fun release() {
        try { codec.stop() } catch (t: Throwable) { /* already stopped or never started */ }
        try { codec.release() } catch (t: Throwable) { /* nothing more to free */ }
        try { muxer.release() } catch (t: Throwable) { /* nothing more to free */ }
    }

    private fun timeUs(): Long = framesWritten * 1_000_000L / sampleRate

    private fun drain(untilEnd: Boolean) {
        var idle = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (untilEnd) TIMEOUT_US else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!untilEnd) return
                    if (++idle > 500) throw IOException("Encoder did not finish")
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) throw IOException("Encoder changed format twice")
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                index >= 0 -> {
                    idle = 0
                    val buffer = codec.getOutputBuffer(index) ?: throw IOException("Encoder gave no output buffer")
                    // Setup data is already in the format given to the muxer.
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxerStarted) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private companion object {
        const val MIME = "audio/mp4a-latm"
        const val TIMEOUT_US = 10_000L
    }
}
