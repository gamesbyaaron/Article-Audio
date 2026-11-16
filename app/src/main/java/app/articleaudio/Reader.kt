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
 * Holds the current article and reads it aloud. Paragraphs are queued ahead in the speech
 * engine so the voice flows without gaps; the engine's progress reports say which sentence is
 * being spoken. Lives for the whole app process, so turning the phone does not stop reading.
 */
object Reader : Speaker.Listener {
    private const val HEADING_PAUSE_MS = 700L
    private const val QUEUE_AHEAD = 3

    var linkInput by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var messageIsError by mutableStateOf(false)
        private set
    var article by mutableStateOf<Article?>(null)
        private set
    /** The sentence being spoken, or where Play will start. */
    var index by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set

    private lateinit var speaker: Speaker
    private val main = Handler(Looper.getMainLooper())

    // Every start gets a new generation number so callbacks from stopped speech are ignored.
    private var generation = 0
    private var queuedThrough = -1
    private var finalId: String? = null
    private var positionsReported = false
    private var positionsLogged = false

    fun init(context: Context) {
        speaker = Speaker(context, this)
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
                val fetched = WikipediaClient.fetchPlainText(requested)
                val built = ArticleText.build(fetched.title, fetched.text, maxChars)
                val ms = System.currentTimeMillis() - started
                main.post {
                    article = built
                    index = 0
                    loading = false
                    show("")
                    DebugLog.i(
                        "Fetched \"${built.title}\" in $ms ms: ${fetched.text.length} characters, " +
                            "${built.chunks.size} paragraphs and headings, ${built.sentences.size} sentences"
                    )
                    val perRule = built.changes.groupingBy { it.rule }.eachCount()
                        .entries.joinToString(", ") { "${it.key} ${it.value}" }
                    DebugLog.i("Cleanup made ${built.changes.size} changes" + if (perRule.isEmpty()) "" else ": $perRule")
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
        val a = article ?: return
        if (!speaker.ready) {
            show("The speech engine is not ready yet. Try again in a moment.", error = true)
            DebugLog.e("Play pressed before text-to-speech was ready")
            return
        }
        if (index >= a.sentences.size) index = 0
        playing = true
        show("")
        DebugLog.i("Play from sentence ${index + 1} of ${a.sentences.size}")
        startFrom(a, index)
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

    fun skip(by: Int) = jumpTo(index + by)

    fun jumpTo(sentence: Int) {
        val a = article ?: return
        index = sentence.coerceIn(0, a.sentences.size - 1)
        if (playing) startFrom(a, index)
    }

    /** Starts speaking at [sentence], replacing whatever is queued. */
    private fun startFrom(a: Article, sentence: Int) {
        generation++
        finalId = null
        val s = a.sentences[sentence]
        if (!enqueue(a, s.chunk, s.start, flush = true)) return
        topUp(a, s.chunk)
    }

    private fun enqueue(a: Article, chunk: Int, offset: Int, flush: Boolean): Boolean {
        val c = a.chunks[chunk]
        val id = "g$generation:$chunk:$offset"
        if (!speaker.speak(c.text.substring(offset), id, flush)) {
            fail("The speech engine refused paragraph ${chunk + 1}")
            return false
        }
        var last = id
        if (c.isHeading && speaker.pause(HEADING_PAUSE_MS, "$id:p")) last = "$id:p"
        queuedThrough = chunk
        if (chunk == a.chunks.lastIndex) finalId = last
        return true
    }

    /** Keeps a few paragraphs queued beyond the one being spoken. */
    private fun topUp(a: Article, current: Int) {
        val target = minOf(current + QUEUE_AHEAD, a.chunks.lastIndex)
        while (queuedThrough < target) {
            if (!enqueue(a, queuedThrough + 1, 0, flush = false)) return
        }
    }

    /** Splits an utterance id into chunk and offset, or null if it is from stopped speech. */
    private fun current(id: String): Pair<Int, Int>? {
        val parts = id.split(':')
        if (!playing || parts.size < 3 || parts[0] != "g$generation") return null
        return parts[1].toInt() to parts[2].toInt()
    }

    override fun onSpeechStart(id: String) {
        val (chunk, offset) = current(id) ?: return
        val a = article ?: return
        if (id.endsWith(":p")) return
        index = a.sentenceAt(chunk, offset)
        topUp(a, chunk)
    }

    override fun onSpeechRange(id: String, start: Int) {
        val (chunk, offset) = current(id) ?: return
        val a = article ?: return
        positionsReported = true
        val sentence = a.sentenceAt(chunk, offset + start)
        if (sentence != index) index = sentence
    }

    override fun onSpeechDone(id: String) {
        val (chunk, _) = current(id) ?: return
        val a = article ?: return
        if (!positionsLogged && !a.chunks[chunk].isHeading) {
            positionsLogged = true
            DebugLog.i(
                if (positionsReported) "Speech engine reports word positions: tracking by sentence"
                else "Speech engine does not report word positions: tracking by paragraph"
            )
        }
        if (id == finalId) {
            playing = false
            index = 0
            DebugLog.i("Finished reading \"${a.title}\"")
        }
    }

    override fun onSpeechError(id: String, code: Int) {
        val (chunk, _) = current(id) ?: return
        fail("Speech error in paragraph ${chunk + 1} (code $code)")
    }

    private fun fail(text: String) {
        DebugLog.e(text)
        playing = false
        generation++
        speaker.stop()
        show(text, error = true)
    }

    private fun show(text: String, error: Boolean = false) {
        message = text
        messageIsError = error
    }
}
