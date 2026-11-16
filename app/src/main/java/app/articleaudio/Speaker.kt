package app.articleaudio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * Thin wrapper around the phone's built-in text-to-speech engine. Callbacks arrive on the
 * main thread.
 */
class Speaker(context: Context, private val listener: Listener) {
    interface Listener {
        fun onSpeechStart(id: String)
        /** The engine is about to speak the word at character [start] of utterance [id]. */
        fun onSpeechRange(id: String, start: Int)
        fun onSpeechDone(id: String)
        fun onSpeechError(id: String, code: Int)
    }

    private val main = Handler(Looper.getMainLooper())
    var ready = false
        private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            DebugLog.i("Text-to-speech ready: engine=${tts.defaultEngine}, voice=${voiceName()}")
        } else {
            DebugLog.e("Text-to-speech failed to start (status $status). Is a TTS engine installed?")
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                main.post { listener.onSpeechStart(utteranceId) }
            }
            override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                main.post { listener.onSpeechRange(utteranceId, start) }
            }
            override fun onDone(utteranceId: String) {
                main.post { listener.onSpeechDone(utteranceId) }
            }
            @Deprecated("Required override")
            override fun onError(utteranceId: String) {
                main.post { listener.onSpeechError(utteranceId, -1) }
            }
            override fun onError(utteranceId: String, errorCode: Int) {
                main.post { listener.onSpeechError(utteranceId, errorCode) }
            }
        })
    }

    /** Longest text the engine accepts in one call. */
    val maxInput: Int get() = TextToSpeech.getMaxSpeechInputLength()

    /** Speaks [text]: straight away if [flush], otherwise after what is already queued. */
    fun speak(text: String, id: String, flush: Boolean): Boolean {
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        return tts.speak(text, mode, null, id) == TextToSpeech.SUCCESS
    }

    /** Queues silence after whatever is queued. */
    fun pause(ms: Long, id: String): Boolean =
        tts.playSilentUtterance(ms, TextToSpeech.QUEUE_ADD, id) == TextToSpeech.SUCCESS

    fun stop() {
        tts.stop()
    }

    private fun voiceName(): String = try { tts.voice?.name ?: "default" } catch (t: Throwable) { "unknown" }
}
