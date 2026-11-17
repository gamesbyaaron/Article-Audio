package app.articleaudio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/** A voice the user can pick on the Settings screen. */
data class VoiceOption(val name: String, val label: String)

/**
 * Thin wrapper around the phone's built-in text-to-speech engine. Callbacks arrive on the
 * main thread.
 */
class Speaker(context: Context, private val listener: Listener) {
    interface Listener {
        fun onSpeechReady()
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
            tts.setAudioAttributes(AudioFocus.SPEECH)
            DebugLog.i("Text-to-speech ready: engine=${tts.defaultEngine}")
            listener.onSpeechReady()
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

    /** Applies to everything spoken after this call. 1.0 is normal speed. */
    fun setRate(rate: Float) {
        tts.setSpeechRate(rate)
    }

    /** Switches to the voice called [name], or the engine's default for null. False if it failed. */
    fun setVoice(name: String?): Boolean = try {
        val voice = if (name == null) tts.defaultVoice else tts.voices?.firstOrNull { it.name == name }
        voice != null && tts.setVoice(voice) == TextToSpeech.SUCCESS
    } catch (t: Throwable) {
        DebugLog.e("Could not set voice ${name ?: "default"}", t)
        false
    }

    /** English voices that work without internet; all offline voices if there are no English ones. */
    fun voiceOptions(): List<VoiceOption> {
        val all = try {
            tts.voices?.toList().orEmpty()
        } catch (t: Throwable) {
            DebugLog.e("Could not list voices", t)
            emptyList()
        }
        val offline = all.filter {
            !it.isNetworkConnectionRequired &&
                it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }
        val english = offline.filter { it.locale.language == "en" }
        val counters = mutableMapOf<String, Int>()
        return english.ifEmpty { offline }
            .sortedWith(compareBy({ it.locale.displayName }, { it.name }))
            .map {
                val accent = it.locale.displayName
                val number = (counters[accent] ?: 0) + 1
                counters[accent] = number
                VoiceOption(it.name, VoiceNames.label(accent, number, it.name))
            }
    }
}
