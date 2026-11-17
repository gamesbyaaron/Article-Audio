package app.articleaudio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Tells Android this app is the one playing audio, so music apps pause while an article is
 * read, and reports when something else (a call, another player) takes over.
 */
class AudioFocus(context: Context, onChange: (Int) -> Unit) {
    private val manager = context.getSystemService(AudioManager::class.java)

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(SPEECH)
        .setOnAudioFocusChangeListener { change -> onChange(change) }
        .build()

    fun request(): Boolean =
        manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    fun abandon() {
        manager.abandonAudioFocusRequest(request)
    }

    companion object {
        val SPEECH: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
