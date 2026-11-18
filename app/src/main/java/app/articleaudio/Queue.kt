package app.articleaudio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * The line-up of articles to play next. Kept across restarts. When an article ends, the next
 * one starts where it was left.
 */
object Queue {
    private lateinit var file: File
    private var state = QueueState()

    /** Ids of the articles coming up, for the screens. */
    var upNext by mutableStateOf<List<String>>(emptyList())
        private set
    var hasPrevious by mutableStateOf(false)
        private set

    fun init(context: Context) {
        file = File(context.filesDir, "queue.txt")
        state = try {
            if (file.exists()) QueueState.parse(file.readText()) else QueueState()
        } catch (t: Throwable) {
            DebugLog.e("Could not read the queue", t)
            QueueState()
        }
        published()
    }

    private val current: String? get() = Reader.entry?.id

    fun playNext(id: String) {
        state.playNext(id, current)
        DebugLog.i("Play next: $id")
        published()
    }

    fun add(id: String) {
        state.add(id, current)
        DebugLog.i("Added to queue: $id")
        published()
    }

    fun remove(index: Int) {
        state.remove(index)
        published()
    }

    fun move(from: Int, to: Int) {
        state.move(from, to)
        published()
    }

    fun clear() {
        state.replace(emptyList())
        published()
    }

    fun forget(id: String) {
        state.forget(id)
        published()
    }

    /** Plays the next article in line. Returns false if there is none. */
    fun next(): Boolean {
        val id = state.takeNext(current) ?: return false
        published()
        DebugLog.i("Queue: next article $id")
        Reader.openAndPlay(id)
        return true
    }

    /** Goes back to the article played before, or to the start of this one if there was none. */
    fun previous() {
        val id = state.takePrevious(current)
        published()
        if (id == null) {
            Reader.restartArticle()
            return
        }
        DebugLog.i("Queue: previous article $id")
        Reader.openAndPlay(id)
    }

    /**
     * Plays a collection: [ids] in order, resuming each where it was left and skipping ones
     * already finished, or from the very start of each if [fromStart].
     */
    fun playAll(ids: List<String>, fromStart: Boolean) {
        val entries = ids.mapNotNull { id -> Library.entries.firstOrNull { it.id == id } }
        if (entries.isEmpty()) return
        val wanted = if (fromStart) {
            entries.forEach { Library.savePosition(it.id, 0, it.sentences, 0) }
            entries
        } else {
            entries.filterNot { it.finished && it.position == 0 }.ifEmpty { entries }
        }
        state.replace(wanted.drop(1).map { it.id })
        published()
        DebugLog.i("Playing ${wanted.size} articles" + if (fromStart) " from the start" else "")
        Reader.openAndPlay(wanted.first().id, restart = fromStart)
    }

    private fun published() {
        upNext = state.upNext.toList()
        hasPrevious = state.history.isNotEmpty()
        if (::file.isInitialized) {
            try {
                file.writeText(state.serialize())
            } catch (t: Throwable) {
                DebugLog.e("Could not save the queue", t)
            }
        }
    }
}
