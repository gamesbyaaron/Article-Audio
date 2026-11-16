package app.articleaudio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.thread

/**
 * Holds the current article and reads it aloud one sentence at a time. Lives for the whole
 * app process (not tied to a screen), so turning the phone does not stop reading.
 */
object Reader {
    private const val HEADING_PAUSE_MS = 700L

    var linkInput by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var messageIsError by mutableStateOf(false)
        private set
    var title by mutableStateOf<String?>(null)
        private set
    var utterances by mutableStateOf<List<Utterance>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set

    private lateinit var speaker: Speaker
    private val main = Handler(Looper.getMainLooper())

    // Each speak call gets a new generation number so callbacks from stopped speech are ignored.
    private var generation = 0
    private var waitingFor: String? = null

    fun init(context: Context) {
        speaker = Speaker(context, ::onSpeechDone, ::onSpeechError)
    }

    fun fetch(input: String) {
        val parsed = WikiLink.parse(input)
        if (parsed is WikiLink.Result.Problem) {
            DebugLog.e("Link not accepted: ${parsed.message} Input: ${input.take(200)}")
            show(parsed.message, error = true)
            return
        }
        val requested = (parsed as WikiLink.Result.Article).title
        stop()
        loading = true
        show("Fetching \"$requested\"…")
        DebugLog.i("Fetching article \"$requested\"")
        val maxChars = minOf(speaker.maxInput - 1, 3000)
        val started = System.currentTimeMillis()
        thread(name = "fetch") {
            try {
                val article = WikipediaClient.fetchPlainText(requested)
                val list = ArticleText.toUtterances(article.title, article.text, maxChars)
                val ms = System.currentTimeMillis() - started
                main.post {
                    title = article.title
                    utterances = list
                    index = 0
                    loading = false
                    show("")
                    DebugLog.i(
                        "Fetched \"${article.title}\" in $ms ms: ${article.text.length} characters, " +
                            "${list.size} sentences and headings"
                    )
                }
            } catch (t: Throwable) {
                DebugLog.e("Fetching \"$requested\" failed", t)
                main.post {
                    loading = false
                    show("Could not fetch the article: ${t.message ?: t.javaClass.simpleName}", error = true)
                }
            }
        }
    }

    fun play() {
        if (utterances.isEmpty()) return
        if (!speaker.ready) {
            show("The speech engine is not ready yet. Try again in a moment.", error = true)
            DebugLog.e("Play pressed before text-to-speech was ready")
            return
        }
        if (index >= utterances.size) index = 0
        playing = true
        show("")
        DebugLog.i("Play from sentence ${index + 1} of ${utterances.size}")
        speakCurrent()
    }

    fun pause() {
        if (!playing) return
        playing = false
        generation++
        speaker.stop()
        DebugLog.i("Paused at sentence ${index + 1}")
    }

    fun stop() {
        pause()
        index = 0
    }

    fun skip(by: Int) {
        if (utterances.isEmpty()) return
        index = (index + by).coerceIn(0, utterances.size - 1)
        if (playing) speakCurrent()
    }

    private fun speakCurrent() {
        val utterance = utterances[index]
        val id = "g${++generation}-$index"
        if (!speaker.speak(utterance.text, id)) {
            fail("The speech engine refused sentence ${index + 1}")
            return
        }
        waitingFor = if (utterance.isHeading && speaker.pause(HEADING_PAUSE_MS, "$id-pause")) "$id-pause" else id
    }

    private fun onSpeechDone(id: String) {
        if (!playing || id != waitingFor) return
        if (index + 1 >= utterances.size) {
            playing = false
            index = 0
            DebugLog.i("Finished reading \"$title\"")
            return
        }
        index++
        speakCurrent()
    }

    private fun onSpeechError(id: String, code: Int) {
        if (!playing || !id.startsWith("g$generation-")) return
        fail("Speech error on sentence ${index + 1} (code $code)")
    }

    private fun fail(text: String) {
        DebugLog.e(text)
        playing = false
        show(text, error = true)
    }

    private fun show(text: String, error: Boolean = false) {
        message = text
        messageIsError = error
    }
}
