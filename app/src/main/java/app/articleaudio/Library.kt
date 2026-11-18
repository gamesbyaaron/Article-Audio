package app.articleaudio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Properties

/**
 * Articles saved on the phone. Each has a folder in app storage holding the text as fetched
 * from Wikipedia and a small details file with the reading position. Works offline.
 */
object Library {
    var entries by mutableStateOf<List<LibraryEntry>>(emptyList())
        private set

    private lateinit var root: File
    private lateinit var downloads: File
    private val main = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        root = File(context.filesDir, "library")
        downloads = File(context.filesDir, "downloads")
        entries = readAll()
    }

    /** The downloaded audio file for an article, whether or not it exists yet. */
    fun audioFile(title: String): File = File(downloads, Wav.fileName(title) + ".m4a")

    /** Where each section starts in the downloaded audio, if that was recorded. */
    fun sectionsFile(title: String): File = File(downloads, Wav.fileName(title) + ".sections")

    fun find(id: String): LibraryEntry? = read(id)

    /** The article's picture, whether or not it exists. */
    fun imageFile(id: String): File = File(File(root, id), "image.jpg")

    /** Stores the article's picture and its credit, or records that it has none. */
    @Synchronized
    fun savePicture(id: String, bytes: ByteArray?, credit: String) {
        val old = read(id) ?: return
        if (bytes != null) imageFile(id).writeBytes(bytes)
        write(old.copy(imageCredit = if (bytes != null) credit else "", imageChecked = true))
        reload()
    }

    fun text(id: String): String = File(File(root, id), "text.txt").readText()

    /** Saves a fetched article. Keeps the reading position unless [resetPosition]. */
    @Synchronized
    fun save(title: String, text: String, sentences: Int, resetPosition: Boolean, description: String = ""): LibraryEntry {
        val id = Wav.fileName(title)
        val dir = File(root, id)
        dir.mkdirs()
        File(dir, "text.txt").writeText(text)
        val old = read(id)
        val now = System.currentTimeMillis()
        val entry = LibraryEntry(
            id = id,
            title = title,
            name = old?.name ?: title,
            fetchedAt = now,
            openedAt = now,
            chars = text.length,
            position = if (resetPosition) 0 else (old?.position ?: 0).coerceIn(0, maxOf(0, sentences - 1)),
            sentences = sentences,
            audioPositionMs = if (resetPosition) 0 else old?.audioPositionMs ?: 0,
            hasAudio = audioFile(title).exists(),
            imageCredit = old?.imageCredit ?: "",
            imageChecked = !resetPosition && old?.imageChecked == true,
            finished = !resetPosition && old?.finished == true,
            description = description.ifEmpty { old?.description.orEmpty() },
        )
        write(entry)
        reload()
        return entry
    }

    @Synchronized
    fun savePosition(id: String, position: Int, sentences: Int, audioPositionMs: Int) {
        val old = read(id) ?: return
        write(old.copy(position = position, sentences = sentences, audioPositionMs = audioPositionMs))
        reload()
    }

    @Synchronized
    fun markFinished(id: String) = setFinished(id, true)

    @Synchronized
    fun setFinished(id: String, finished: Boolean) {
        val old = read(id) ?: return
        write(old.copy(finished = finished))
        reload()
    }

    @Synchronized
    fun markOpened(id: String) {
        val old = read(id) ?: return
        write(old.copy(openedAt = System.currentTimeMillis()))
        reload()
    }

    @Synchronized
    fun rename(id: String, name: String) {
        val old = read(id) ?: return
        write(old.copy(name = name.trim().ifEmpty { old.title }))
        reload()
    }

    /** Removes the article and its downloaded audio from the app. A copy in Music stays. */
    @Synchronized
    fun delete(id: String) {
        val old = read(id)
        File(root, id).deleteRecursively()
        if (old != null) {
            audioFile(old.title).delete()
            sectionsFile(old.title).delete()
        }
        Queue.forget(id)
        Collections.forget(id)
        DebugLog.i("Deleted \"${old?.title ?: id}\" from the library")
        reload()
    }

    /** Re-reads the folder, for example after a download finishes. Safe from any thread. */
    fun reload() {
        val list = readAll()
        main.post { entries = list }
    }

    private fun readAll(): List<LibraryEntry> =
        (root.listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .mapNotNull { read(it.name) }
            .sortedByDescending { it.openedAt }

    private fun read(id: String): LibraryEntry? = try {
        val file = File(File(root, id), "details.properties")
        if (!file.exists()) null
        else {
            val p = Properties()
            file.inputStream().use { p.load(it) }
            val title = p.getProperty("title")
            LibraryEntry.fromProperties(id, p, hasAudio = title != null && audioFile(title).exists())
        }
    } catch (t: Throwable) {
        DebugLog.e("Could not read library entry $id", t)
        null
    }

    private fun write(entry: LibraryEntry) {
        try {
            val dir = File(root, entry.id)
            dir.mkdirs()
            val tmp = File(dir, "details.properties.tmp")
            tmp.outputStream().use { entry.toProperties().store(it, null) }
            tmp.renameTo(File(dir, "details.properties"))
        } catch (t: Throwable) {
            DebugLog.e("Could not save library entry ${entry.id}", t)
        }
    }
}
