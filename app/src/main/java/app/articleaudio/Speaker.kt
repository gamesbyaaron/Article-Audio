package app.articleaudio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/** Thin wrapper around the phone's built-in text-to-speech engine, logging every step. */
class Speaker(context: Context) {
    private var ready = false
    private var counter = 0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            DebugLog.i("Text-to-speech ready: engine=${engineName()}, voice=${voiceName()}")
        } else {
            DebugLog.e("Text-to-speech failed to start (status $status). Is a TTS engine installed?")
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = DebugLog.i("Speaking started ($utteranceId)")
            override fun onDone(utteranceId: String) = DebugLog.i("Speaking finished ($utteranceId)")
            override fun onStop(utteranceId: String, interrupted: Boolean) =
                DebugLog.i("Speaking stopped ($utteranceId)")
            @Deprecated("Required override")
            override fun onError(utteranceId: String) = DebugLog.e("Speaking error ($utteranceId)")
            override fun onError(utteranceId: String, errorCode: Int) =
                DebugLog.e("Speaking error ($utteranceId), code $errorCode")
        })
    }

    /** Returns null on success, or a short message explaining why it could not speak. */
    fun speak(text: String): String? {
        if (!ready) return "Speech engine not ready yet".also { DebugLog.e(it) }
        if (text.isBlank()) return "Nothing to speak"
        val max = TextToSpeech.getMaxSpeechInputLength()
        if (text.length > max) {
            return "Text too long (${text.length} of max $max characters)".also { DebugLog.e(it) }
        }
        val id = "u${++counter}"
        DebugLog.i("Speak requested ($id): ${text.length} characters")
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        return if (result == TextToSpeech.SUCCESS) null
        else "Speak call failed (code $result)".also { DebugLog.e(it) }
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }

    private fun engineName(): String = tts.defaultEngine ?: "unknown"
    private fun voiceName(): String = try { tts.voice?.name ?: "default" } catch (t: Throwable) { "unknown" }
}
