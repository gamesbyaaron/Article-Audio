package app.articleaudio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Makes an .m4a audio file of an article: the speech engine writes each paragraph to a WAV
 * file, which is encoded straight into the .m4a and deleted. Uses its own speech engine
 * connection so it does not disturb live reading. Lives for the whole app process.
 */
object Exporter {
    private const val HEADING_PAUSE_MS = 700
    private const val PARAGRAPH_PAUSE_MS = 250
    private const val FOLDER = "Music/Article Audio"

    var running by mutableStateOf(false)
        private set
    /** 0 to 1 while running. */
    var progress by mutableFloatStateOf(0f)
        private set
    /** Title of the article being made or last finished. */
    var title by mutableStateOf("")
        private set
    /** Where the finished file is, described for the user; empty if none or failed. */
    var savedAs by mutableStateOf("")
        private set
    /** Shareable address of the finished file, where Android provides one. */
    var savedUri by mutableStateOf<Uri?>(null)
        private set
    var error by mutableStateOf("")
        private set

    /** Called on the main thread when anything above changes, for the download service. */
    var onChanged: (() -> Unit)? = null

    private lateinit var appContext: Context
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var cancelled = false
    @Volatile private var activeTts: TextToSpeech? = null

    private class Cancelled : Exception()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun start(article: Article) {
        if (running) return
        running = true
        cancelled = false
        progress = 0f
        title = article.title
        savedAs = ""
        savedUri = null
        error = ""
        DebugLog.i("Download started: \"${article.title}\", ${article.chunks.size} paragraphs and headings")
        ExportService.start(appContext)
        onChanged?.invoke()
        thread(name = "export") { run(article) }
    }

    fun cancel() {
        if (!running) return
        DebugLog.i("Download cancel requested")
        cancelled = true
        try { activeTts?.stop() } catch (t: Throwable) { /* the worker thread notices the flag */ }
    }

    private fun run(article: Article) {
        val started = System.currentTimeMillis()
        val work = File(appContext.cacheDir, "export")
        val downloads = File(appContext.filesDir, "downloads")
        val name = Wav.fileName(article.title)
        val part = File(downloads, "$name.m4a.part")
        val target = File(downloads, "$name.m4a")
        var tts: TextToSpeech? = null
        var writer: AacWriter? = null
        try {
            work.deleteRecursively()
            if (!work.mkdirs() && !work.isDirectory) throw IOException("Could not create a working folder")
            if (!downloads.mkdirs() && !downloads.isDirectory) throw IOException("Could not create the downloads folder")
            part.delete()

            val synth = Synth()
            tts = synth.connect()
            activeTts = tts

            val credit = "This was ${article.title}, from Wikipedia. " +
                "The text is available under the Creative Commons Attribution-ShareAlike license."
            val pieces = article.chunks.map { it.text to it.isHeading } + (credit to false)
            val totalChars = pieces.sumOf { it.first.length }.coerceAtLeast(1)
            var doneChars = 0
            val buffer = ByteArray(32_768)

            for ((i, piece) in pieces.withIndex()) {
                if (cancelled) throw Cancelled()
                val (text, isHeading) = piece
                val wav = File(work, "piece$i.wav")
                synth.toFile(text, wav, "export$i")
                if (cancelled) throw Cancelled()

                BufferedInputStream(wav.inputStream()).use { input ->
                    val info = Wav.readHeader(input)
                    if (info.bitsPerSample != 16) throw IOException("Speech engine wrote ${info.bitsPerSample}-bit sound; only 16-bit is supported")
                    val w = writer ?: AacWriter(part, info.sampleRate, info.channels).also {
                        writer = it
                        DebugLog.i("Download sound format: ${info.sampleRate} Hz, ${info.channels} channel(s), 16-bit")
                    }
                    if (i == 0) w.silence(PARAGRAPH_PAUSE_MS)
                    while (true) {
                        val count = readFully(input, buffer)
                        if (count <= 0) break
                        w.write(buffer, count)
                        if (count < buffer.size) break
                    }
                    w.silence(if (isHeading) HEADING_PAUSE_MS else PARAGRAPH_PAUSE_MS)
                }
                wav.delete()

                doneChars += text.length
                val fraction = doneChars.toFloat() / totalChars
                main.post {
                    progress = fraction
                    onChanged?.invoke()
                }
            }

            val w = writer ?: throw IOException("The article has no text")
            w.finish()
            val seconds = w.seconds
            w.release()
            writer = null
            target.delete()
            if (!part.renameTo(target)) throw IOException("Could not save the finished file")

            val uri = publish(target, name, article.title)
            val took = (System.currentTimeMillis() - started) / 1000
            DebugLog.i(
                "Download finished: ${target.length() / 1024} KB, $seconds s of audio, made in $took s. " +
                    if (uri != null) "Copied to $FOLDER/$name.m4a" else "Kept inside the app only (Android 9 or older)"
            )
            finish(saved = if (uri != null) "$FOLDER/$name.m4a" else "inside the app ($name.m4a)", uri = uri, problem = "")
        } catch (c: Cancelled) {
            DebugLog.i("Download cancelled")
            part.delete()
            finish(saved = "", uri = null, problem = "")
        } catch (t: Throwable) {
            DebugLog.e("Download failed", t)
            part.delete()
            finish(saved = "", uri = null, problem = "Download failed: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            activeTts = null
            try { writer?.release() } catch (t: Throwable) { /* already released */ }
            try { tts?.shutdown() } catch (t: Throwable) { /* nothing to shut down */ }
            work.deleteRecursively()
        }
    }

    private fun finish(saved: String, uri: Uri?, problem: String) {
        main.post {
            running = false
            savedAs = saved
            savedUri = uri
            error = problem
            onChanged?.invoke()
        }
    }

    /** Fills [buffer] as far as the stream allows; returns the number of bytes read. */
    private fun readFully(input: BufferedInputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val count = input.read(buffer, total, buffer.size - total)
            if (count < 0) break
            total += count
        }
        return total
    }

    /** Copies the file to the phone's shared Music folder (Android 10+), so other apps can see it. */
    private fun publish(file: File, name: String, articleTitle: String): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.TITLE, articleTitle)
            put(MediaStore.Audio.Media.ARTIST, "Wikipedia")
            put(MediaStore.Audio.Media.RELATIVE_PATH, FOLDER)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("Android refused to create the shared file")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Could not open the shared file")
            out.use { file.inputStream().use { input -> input.copyTo(it) } }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    /** A private speech engine connection that writes one piece of text to a file at a time. */
    private class Synth {
        private lateinit var tts: TextToSpeech
        @Volatile private var waiting: CountDownLatch? = null
        @Volatile private var failure: String? = null

        fun connect(): TextToSpeech {
            val ready = CountDownLatch(1)
            var status = TextToSpeech.ERROR
            tts = TextToSpeech(appContext) {
                status = it
                ready.countDown()
            }
            if (!ready.await(20, TimeUnit.SECONDS)) throw IOException("The speech engine did not start")
            if (status != TextToSpeech.SUCCESS) throw IOException("The speech engine failed to start (status $status)")

            val voiceName = Prefs.voiceName
            if (voiceName != null) {
                try {
                    tts.voices?.firstOrNull { it.name == voiceName }?.let { tts.voice = it }
                } catch (t: Throwable) {
                    DebugLog.e("Download: could not set voice $voiceName; using the default", t)
                }
            }
            tts.setSpeechRate(Prefs.speed)
            DebugLog.i("Download voice: ${voiceName ?: "the phone's default"} at speed ${Prefs.speed}")

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) {
                    waiting?.countDown()
                }
                override fun onStop(utteranceId: String, interrupted: Boolean) {
                    failure = "stopped"
                    waiting?.countDown()
                }
                @Deprecated("Required override")
                override fun onError(utteranceId: String) {
                    failure = "speech engine error"
                    waiting?.countDown()
                }
                override fun onError(utteranceId: String, errorCode: Int) {
                    failure = "speech engine error $errorCode"
                    waiting?.countDown()
                }
            })
            return tts
        }

        fun toFile(text: String, file: File, id: String) {
            val latch = CountDownLatch(1)
            failure = null
            waiting = latch
            val result = tts.synthesizeToFile(text, Bundle(), file, id)
            if (result != TextToSpeech.SUCCESS) throw IOException("The speech engine refused the text (code $result)")
            if (!latch.await(10, TimeUnit.MINUTES)) throw IOException("The speech engine took too long on one paragraph")
            if (cancelled) throw Cancelled()
            failure?.let { throw IOException("Could not make speech for a paragraph: $it") }
            if (!file.exists() || file.length() < 44) throw IOException("The speech engine wrote no sound")
        }
    }
}
