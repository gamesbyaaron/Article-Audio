package app.articleaudio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * Thin wrapper around the phone's built-in text-to-speech engine. Callbacks arrive on the
 * main thread. Every step is written to the debug log.
 */
class Speaker(context: Context, private val onDone: (String) -> Unit, private val onError: (String, Int) -> Unit) {
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
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) {
                main.post { onDone(utteranceId) }
            }
            @Deprecated("Required override")
            override fun onError(utteranceId: String) {
                main.post { onError(utteranceId, -1) }
            }
            override fun onError(utteranceId: String, errorCode: Int) {
                main.post { onError(utteranceId, errorCode) }
            }
        })
    }

    /** Longest text the engine accepts in one call. */
    val maxInput: Int get() = TextToSpeech.getMaxSpeechInputLength()

    /** Speaks [text] now, replacing anything playing. Returns false if the engine refused. */
    fun speak(text: String, id: String): Boolean =
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS

    /** Queues silence after whatever is playing. */
    fun pause(ms: Long, id: String): Boolean =
        tts.playSilentUtterance(ms, TextToSpeech.QUEUE_ADD, id) == TextToSpeech.SUCCESS

    fun stop() {
        tts.stop()
    }

    private fun voiceName(): String = try { tts.voice?.name ?: "default" } catch (t: Throwable) { "unknown" }
}
