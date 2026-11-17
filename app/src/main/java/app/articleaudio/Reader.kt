package app.articleaudio

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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

    /** Built-in voices speak roughly this many characters a second at normal speed. */
    const val CHARS_PER_SECOND = 15

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
    /** True from Play until Stop or the end of the article, including while paused. */
    var active by mutableStateOf(false)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    /** The chosen voice, or null for the phone's default. */
    var voiceName by mutableStateOf<String?>(null)
        private set
    var voices by mutableStateOf<List<VoiceOption>>(emptyList())
        private set

    /** The library entry for [article]. */
    var entry by mutableStateOf<LibraryEntry?>(null)
        private set
    /** True when playing the downloaded audio file instead of reading live. */
    var audioMode by mutableStateOf(false)
        private set
    var audioPositionMs by mutableIntStateOf(0)
        private set
    var audioDurationMs by mutableIntStateOf(0)
        private set

    /** Sleep timer: "" (off), "time", "section" or "article". */
    var sleepMode by mutableStateOf("")
        private set
    /** When a "time" sleep timer runs out, as System.currentTimeMillis(). */
    var sleepEndsAt by mutableLongStateOf(0L)
        private set

    /**
     * An article looked at on its own page before deciding to play it. Its text is fetched so
     * its length can be shown; it joins the library only when played or downloaded.
     */
    class Preview(val title: String, val description: String, val thumbnail: String?) {
        var article by mutableStateOf<Article?>(null)
        var text = ""
        var loading by mutableStateOf(true)
        var error by mutableStateOf("")
    }

    var preview by mutableStateOf<Preview?>(null)
        private set

    /** Called whenever playing or active changes, so the playback service can update. */
    var onPlaybackChanged: (() -> Unit)? = null

    private lateinit var appContext: Context
    private lateinit var speaker: Speaker
    private lateinit var focus: AudioFocus
    private val main = Handler(Looper.getMainLooper())
    private var resumeWhenFocusReturns = false
    private var media: MediaPlayer? = null
    private var sectionTimes: Map<Int, Long> = emptyMap()
    private var sleepSectionEnd = -1 // sentence that ends a "section" timer
    private var sleepSectionEndMs = -1 // or its time in the audio file
    private var stopAtNextSentence = -1 // a timed sleep stops when this sentence is finished
    private var fadeStep = -1 // counts down while the audio file fades out

    private val sleepTick = object : Runnable {
        override fun run() {
            if (sleepMode.isEmpty()) return
            if (sleepMode == "time" && System.currentTimeMillis() >= sleepEndsAt) {
                sleepTimeUp()
                return
            }
            if (sleepMode == "section" && playing) {
                val reached = if (audioMode) sleepSectionEndMs in 0..audioPositionMs
                else sleepSectionEnd in 0..index
                if (reached) {
                    DebugLog.i("Sleep timer: end of section reached")
                    clearSleep()
                    pause()
                    return
                }
            }
            main.postDelayed(this, 500)
        }
    }

    private val fadeTick = object : Runnable {
        override fun run() {
            val player = media ?: return
            if (fadeStep <= 0) {
                fadeStep = -1
                pause()
                try { player.setVolume(1f, 1f) } catch (t: Throwable) { /* released */ }
                return
            }
            val level = fadeStep / 20f
            try { player.setVolume(level, level) } catch (t: Throwable) { /* released */ }
            fadeStep--
            main.postDelayed(this, 300)
        }
    }

    /** Starts a sleep timer: [mode] "time" with [minutes], "section" or "article". */
    fun setSleep(mode: String, minutes: Int = 0) {
        clearSleep()
        sleepMode = mode
        when (mode) {
            "time" -> sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L
            "section" -> {
                val a = article ?: return
                val next = a.sections.getOrNull(a.sectionAt(index) + 1)
                sleepSectionEnd = next?.sentence ?: -1
                sleepSectionEndMs = if (next != null) SectionTimes.startMs(a, next.sentence, sectionTimes, audioDurationMs) else -1
            }
        }
        DebugLog.i("Sleep timer set: $mode" + if (mode == "time") " $minutes min" else "")
        main.post(sleepTick)
    }

    fun clearSleep() {
        main.removeCallbacks(sleepTick)
        main.removeCallbacks(fadeTick)
        if (fadeStep >= 0) try { media?.setVolume(1f, 1f) } catch (t: Throwable) { /* released */ }
        fadeStep = -1
        sleepMode = ""
        sleepEndsAt = 0L
        sleepSectionEnd = -1
        sleepSectionEndMs = -1
        stopAtNextSentence = -1
    }

    /** A timed sleep ran out: fade the audio file, or let live reading finish its sentence. */
    private fun sleepTimeUp() {
        DebugLog.i("Sleep timer: time is up")
        sleepMode = ""
        if (!playing) return
        Ambient.fadeOut()
        if (audioMode) {
            fadeStep = 20
            main.post(fadeTick)
        } else {
            stopAtNextSentence = index
        }
    }

    /** Jumps to a section of the article, live or in the downloaded audio. */
    fun jumpToSection(section: ArticleSection) {
        val a = article ?: return
        DebugLog.i("Jump to section \"${section.title}\"")
        if (audioMode) {
            seekAudio(SectionTimes.startMs(a, section.sentence, sectionTimes, audioDurationMs))
        } else {
            jumpTo(section.sentence)
        }
    }

    private fun notifyChanged() {
        onPlaybackChanged?.invoke()
        Ambient.follow(playing)
    }

    private var lastSave = 0L
    private var playWhenOpened = false

    /** While the downloaded audio plays, keeps the progress bar and the saved place up to date. */
    private val tick = object : Runnable {
        override fun run() {
            if (!playing || !audioMode) return
            try {
                media?.let { audioPositionMs = it.currentPosition }
            } catch (t: Throwable) { /* the player was released; the next state change sorts it out */ }
            savePosition(force = false)
            main.postDelayed(this, 500)
        }
    }

    // Every start gets a new generation number so callbacks from stopped speech are ignored.
    private var generation = 0
    private var queuedThrough = -1
    private var finalId: String? = null
    private var positionsReported = false
    private var positionsLogged = false

    fun init(context: Context) {
        appContext = context.applicationContext
        speed = Prefs.speed
        voiceName = Prefs.voiceName
        focus = AudioFocus(appContext, ::onFocusChange)
        speaker = Speaker(appContext, this)
    }

    override fun onSpeechReady() {
        speaker.setRate(speed)
        val saved = voiceName
        if (saved != null && !speaker.setVoice(saved)) {
            DebugLog.e("Saved voice \"$saved\" is not available; using the phone's default")
            voiceName = null
            Prefs.voiceName = null
        }
        voices = speaker.voiceOptions()
        DebugLog.i(
            "Voices to choose from: ${voices.size}. Using ${voiceName ?: "the phone's default"} at speed $speed"
        )
    }

    fun changeSpeed(value: Float) {
        speed = value
        Prefs.speed = value
        speaker.setRate(value)
        DebugLog.i("Speed set to $value")
        applyNow()
    }

    fun changeVoice(name: String?) {
        if (!speaker.setVoice(name) && name != null) {
            show("That voice could not be used.", error = true)
            return
        }
        voiceName = name
        Prefs.voiceName = name
        DebugLog.i("Voice set to ${name ?: "the phone's default"}")
        applyNow()
    }

    /** Makes a new voice or speed audible straight away. */
    private fun applyNow() {
        val a = article
        if (audioMode) {
            if (playing) applyAudioSpeed()
        } else if (playing && a != null) {
            startFrom(a, index)
        } else if (speaker.ready && !active) {
            speaker.speak("This is how the reading will sound.", "sample", flush = true)
        }
    }

    fun fetch(input: String) {
        val parsed = WikiLink.parse(input)
        if (parsed is WikiLink.Result.Problem) {
            DebugLog.e("Link not accepted: ${parsed.message} Input: ${input.take(200)}")
            show(parsed.message, error = true)
            return
        }
        fetchTitle((parsed as WikiLink.Result.Article).title, resetPosition = false)
    }

    /** Fetches an article picked from search. */
    fun fetchByTitle(title: String) = fetchTitle(title, resetPosition = false)

    /** Fetches the open article again from Wikipedia and starts it from the beginning. */
    fun refresh() {
        val e = entry ?: return
        fetchTitle(e.title, resetPosition = true)
    }

    private fun fetchTitle(requested: String, resetPosition: Boolean) {
        leave()
        loading = true
        show("Fetching \"$requested\"…")
        DebugLog.i("Fetching article \"$requested\"")
        val maxChars = maxChunk()
        val started = System.currentTimeMillis()
        thread(name = "fetch") {
            try {
                val fetched = WikipediaClient.fetchPlainText(requested)
                val built = ArticleText.build(fetched.title, fetched.text, maxChars)
                val saved = Library.save(fetched.title, fetched.text, built.sentences.size, resetPosition)
                if (!saved.imageChecked) thread(name = "picture") { fetchPicture(saved) }
                val ms = System.currentTimeMillis() - started
                main.post {
                    display(built, saved)
                    DebugLog.i(
                        "Fetched \"${built.title}\" in $ms ms: ${fetched.text.length} characters, " +
                            "${built.chunks.size} paragraphs and headings, ${built.sentences.size} sentences. Saved to the library"
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

    /** Opens a saved article from the library, at the place it was left. Works offline. */
    fun open(id: String) {
        if (entry?.id == id && article != null) return
        leave()
        loading = true
        show("Opening…")
        val maxChars = maxChunk()
        thread(name = "open") {
            try {
                val saved = Library.find(id) ?: throw IllegalStateException("It is no longer in the library")
                val built = ArticleText.build(saved.title, Library.text(id), maxChars)
                if (!saved.imageChecked) thread(name = "picture") { fetchPicture(saved) }
                main.post {
                    display(built, saved)
                    DebugLog.i("Opened \"${saved.title}\" from the library at sentence ${index + 1} of ${built.sentences.size}")
                }
            } catch (t: Throwable) {
                DebugLog.e("Opening library entry $id failed", t)
                main.post {
                    playWhenOpened = false
                    loading = false
                    show("Could not open the article: ${t.message ?: t.javaClass.simpleName}", error = true)
                }
            }
        }
    }

    /** Looks for a free-licensed picture for the article. Runs on a background thread; never fails loudly. */
    private fun fetchPicture(entry: LibraryEntry) {
        try {
            val picture = WikipediaClient.fetchPicture(entry.title)
            Library.savePicture(entry.id, picture?.bytes, picture?.credit.orEmpty())
            DebugLog.i(
                if (picture != null) "Picture for \"${entry.title}\": ${picture.bytes.size / 1024} KB. ${picture.credit}"
                else "No free-licensed picture for \"${entry.title}\""
            )
        } catch (t: Throwable) {
            DebugLog.e("Could not get a picture for \"${entry.title}\" (will try again next time)", t)
        }
    }

    private fun sentenceChanged() {
        if (stopAtNextSentence >= 0 && index != stopAtNextSentence) {
            DebugLog.i("Sleep timer: stopping at the end of the sentence")
            stopAtNextSentence = -1
            pause()
        }
    }

    /** Shows an article's page: its details come from the library if saved, otherwise from Wikipedia. */
    fun openPreview(title: String, description: String, thumbnail: String?) {
        val p = Preview(title, description, thumbnail)
        preview = p
        val maxChars = maxChunk()
        val saved = Library.entries.firstOrNull { it.title == title }
        thread(name = "preview") {
            try {
                val (realTitle, text) = if (saved != null) saved.title to Library.text(saved.id)
                else WikipediaClient.fetchPlainText(title).let { it.title to it.text }
                val built = ArticleText.build(realTitle, text, maxChars)
                main.post {
                    p.text = text
                    p.article = built
                    p.loading = false
                }
            } catch (t: Throwable) {
                DebugLog.e("Could not load \"$title\" for its page", t)
                main.post {
                    p.loading = false
                    p.error = "Could not load the article: ${t.message ?: t.javaClass.simpleName}"
                }
            }
        }
    }

    /** Saves the previewed article to the library, then plays it or makes its audio file. */
    fun startPreview(download: Boolean) {
        val p = preview ?: return
        val a = p.article ?: return
        val open = entry
        if (!download && open != null && open.title == a.title && article != null) {
            if (!playing) play()
            return
        }
        thread(name = "save") {
            val saved = Library.find(Wav.fileName(a.title))
                ?: Library.save(a.title, p.text, a.sentences.size, resetPosition = false)
            if (!saved.imageChecked) thread(name = "picture") { fetchPicture(saved) }
            main.post {
                if (download) {
                    DebugLog.i("Saved \"${a.title}\" to the library to download it")
                    Exporter.start(a)
                } else {
                    leave()
                    display(a, saved)
                    DebugLog.i("Playing \"${a.title}\" from its page at sentence ${index + 1}")
                    play()
                }
            }
        }
    }

    /** Opens a library article and starts playing where it was left. */
    fun openAndPlay(id: String) {
        if (entry?.id == id && article != null) {
            if (!playing) play()
            return
        }
        playWhenOpened = true
        open(id)
    }

    /** Closes the open article, for example after it is deleted. */
    fun close() {
        leave()
        article = null
        entry = null
    }

    private fun maxChunk(): Int = minOf(speaker.maxInput - 1, 3000)

    private fun display(built: Article, saved: LibraryEntry) {
        article = built
        entry = saved
        index = saved.position.coerceIn(0, built.sentences.size - 1)
        audioPositionMs = saved.audioPositionMs
        audioDurationMs = 0
        loading = false
        show("")
        Library.markOpened(saved.id)
        if (playWhenOpened) {
            playWhenOpened = false
            play()
        }
    }

    /** Stops playing and saves the place, before switching to another article. */
    private fun leave() {
        if (sleepMode == "section" || sleepMode == "article") clearSleep()
        pause()
        savePosition(force = true)
        releaseMedia()
        audioMode = false
        if (active) {
            active = false
            notifyChanged()
        }
    }

    private fun savePosition(force: Boolean) {
        val e = entry ?: return
        val a = article ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastSave < 5_000) return
        lastSave = now
        Library.savePosition(e.id, index, a.sentences.size, audioPositionMs)
    }

    /** Switches between reading live and playing the downloaded audio file. */
    fun useAudio(on: Boolean) {
        if (on == audioMode) return
        pause()
        if (on) {
            if (!prepareMedia()) return
        } else {
            releaseMedia()
        }
        audioMode = on
        DebugLog.i(if (on) "Switched to the downloaded audio" else "Switched to live reading")
    }

    private fun prepareMedia(): Boolean {
        val e = entry ?: return false
        val file = Library.audioFile(e.title)
        if (!file.exists()) {
            show("There is no downloaded audio for this article yet.", error = true)
            return false
        }
        return try {
            val player = MediaPlayer()
            player.setAudioAttributes(AudioFocus.SPEECH)
            player.setDataSource(file.path)
            player.prepare()
            player.setOnCompletionListener { finished() }
            player.setOnErrorListener { _, what, extra ->
                fail("The audio file could not be played (error $what, $extra)")
                true
            }
            audioDurationMs = player.duration
            val timesFile = Library.sectionsFile(e.title)
            sectionTimes = if (timesFile.exists()) SectionTimes.parse(timesFile.readText()) else emptyMap()
            val start = audioPositionMs.coerceIn(0, maxOf(0, player.duration - 1000))
            player.seekTo(start)
            audioPositionMs = start
            media = player
            DebugLog.i("Downloaded audio ready: ${player.duration / 1000} s, resuming at ${start / 1000} s")
            true
        } catch (t: Throwable) {
            DebugLog.e("Could not open the downloaded audio", t)
            show("The downloaded audio could not be opened.", error = true)
            false
        }
    }

    private fun releaseMedia() {
        main.removeCallbacks(tick)
        sectionTimes = emptyMap()
        try { media?.release() } catch (t: Throwable) { /* nothing left to free */ }
        media = null
    }

    /** The speed button applies on top of whatever speed the file was made at. */
    private fun applyAudioSpeed() {
        val player = media ?: return
        try {
            player.playbackParams = player.playbackParams.setSpeed(speed)
        } catch (t: Throwable) {
            DebugLog.e("Could not change the speed of the audio file", t)
        }
    }

    private fun seekAudio(ms: Int) {
        val player = media ?: return
        val target = ms.coerceIn(0, maxOf(0, audioDurationMs - 500))
        try {
            player.seekTo(target)
            audioPositionMs = target
            savePosition(force = true)
        } catch (t: Throwable) {
            DebugLog.e("Could not move within the audio file", t)
        }
    }

    /** The end of the article was reached, live or in the audio file. */
    private fun finished() {
        val name = article?.title
        playing = false
        active = false
        index = 0
        audioPositionMs = 0
        if (audioMode) {
            try { media?.seekTo(0) } catch (t: Throwable) { /* it is recreated on the next switch */ }
        }
        focus.abandon()
        savePosition(force = true)
        entry?.let { Library.markFinished(it.id) }
        if (sleepMode.isNotEmpty()) clearSleep()
        DebugLog.i("Finished \"$name\"")
        notifyChanged()
    }

    fun play() {
        val a = article ?: return
        val player = media
        if (audioMode && player == null) return
        if (!audioMode && !speaker.ready) {
            show("The speech engine is not ready yet. Try again in a moment.", error = true)
            DebugLog.e("Play pressed before text-to-speech was ready")
            return
        }
        if (index >= a.sentences.size) index = 0
        resumeWhenFocusReturns = false
        if (!focus.request()) DebugLog.e("Android did not grant audio focus; playing anyway")
        playing = true
        active = true
        show("")
        if (audioMode && player != null) {
            DebugLog.i("Play downloaded audio from ${audioPositionMs / 1000} s")
            try {
                player.start()
                applyAudioSpeed()
                main.removeCallbacks(tick)
                main.post(tick)
            } catch (t: Throwable) {
                DebugLog.e("Could not start the audio file", t)
                fail("The audio file could not be played")
                return
            }
        } else {
            DebugLog.i("Play from sentence ${index + 1} of ${a.sentences.size}")
            startFrom(a, index)
        }
        PlaybackService.start(appContext)
        notifyChanged()
    }

    fun pause() {
        resumeWhenFocusReturns = false
        if (!playing) return
        halt()
        focus.abandon()
    }

    /** Stops the voice but keeps the place. */
    private fun halt() {
        playing = false
        if (audioMode) {
            try {
                media?.let {
                    it.pause()
                    audioPositionMs = it.currentPosition
                }
            } catch (t: Throwable) {
                DebugLog.e("Could not pause the audio file", t)
            }
            DebugLog.i("Paused the audio at ${audioPositionMs / 1000} s")
        } else {
            generation++
            speaker.stop()
            DebugLog.i("Paused at sentence ${index + 1}")
        }
        savePosition(force = true)
        notifyChanged()
    }

    /** Stops and goes back to the start of the article. */
    fun stop() {
        pause()
        if (audioMode) seekAudio(0) else index = 0
        savePosition(force = true)
        if (active) {
            active = false
            notifyChanged()
        }
    }

    /** A call or another app took over the sound, or gave it back. */
    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> if (playing) {
                DebugLog.i("Another app took over the sound: pausing")
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (playing) {
                DebugLog.i("Sound interrupted for a moment: pausing until it returns")
                halt()
                resumeWhenFocusReturns = true
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeWhenFocusReturns) {
                DebugLog.i("Sound returned: resuming")
                play()
            }
        }
    }

    fun skip(by: Int) = jumpTo(index + by)

    /** Jumps roughly [seconds] of listening forwards or backwards, to the start of a sentence. */
    fun skipSeconds(seconds: Int) {
        val a = article ?: return
        if (audioMode) {
            seekAudio(audioPositionMs + seconds * 1000)
            return
        }
        val target = a.startChars[index] + (seconds * CHARS_PER_SECOND * speed).toInt()
        var sentence = a.sentenceAtChar(target.coerceIn(0, maxOf(0, a.totalChars - 1)))
        if (seconds < 0 && sentence >= index) sentence = index - 1
        if (seconds > 0 && sentence <= index) sentence = index + 1
        jumpTo(sentence)
    }

    /** Jumps to a point in the article, where 0 is the start and 1 is the end. */
    fun seekTo(fraction: Float) {
        val a = article ?: return
        if (audioMode) {
            seekAudio((fraction * audioDurationMs).toInt())
            return
        }
        jumpTo(a.sentenceAtChar((fraction * a.totalChars).toInt()))
    }

    fun jumpTo(sentence: Int) {
        val a = article ?: return
        if (audioMode) return
        index = sentence.coerceIn(0, a.sentences.size - 1)
        if (playing) startFrom(a, index)
        savePosition(force = true)
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
        val started = a.sentenceAt(chunk, offset)
        if (started != index) {
            index = started
            sentenceChanged()
        }
        savePosition(force = false)
        topUp(a, chunk)
    }

    override fun onSpeechRange(id: String, start: Int) {
        val (chunk, offset) = current(id) ?: return
        val a = article ?: return
        positionsReported = true
        val sentence = a.sentenceAt(chunk, offset + start)
        if (sentence != index) {
            index = sentence
            sentenceChanged()
        }
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
        if (id == finalId) finished()
    }

    override fun onSpeechError(id: String, code: Int) {
        val (chunk, _) = current(id) ?: return
        fail("Speech error in paragraph ${chunk + 1} (code $code)")
    }

    private fun fail(text: String) {
        DebugLog.e(text)
        playing = false
        active = false
        generation++
        speaker.stop()
        try { media?.pause() } catch (t: Throwable) { /* not playing */ }
        focus.abandon()
        show(text, error = true)
        notifyChanged()
    }

    private fun show(text: String, error: Boolean = false) {
        message = text
        messageIsError = error
    }
}
