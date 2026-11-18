package app.articleaudio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** The owner's collections of articles. An article can be in any number of them. */
object Collections {
    private lateinit var file: File

    var all by mutableStateOf<List<ArticleCollection>>(emptyList())
        private set

    fun init(context: Context) {
        file = File(context.filesDir, "collections.txt")
        all = try {
            if (file.exists()) CollectionFormat.parse(file.readText()) else emptyList()
        } catch (t: Throwable) {
            DebugLog.e("Could not read collections", t)
            emptyList()
        }
    }

    fun find(id: String): ArticleCollection? = all.firstOrNull { it.id == id }

    /** Creates a collection, optionally with a first article. Returns its id. */
    fun create(name: String, firstItem: String? = null): String {
        val id = "c" + System.currentTimeMillis()
        save(all + ArticleCollection(id, name.trim().ifEmpty { "New collection" }, listOfNotNull(firstItem)))
        DebugLog.i("Created collection \"$name\"")
        return id
    }

    fun rename(id: String, name: String) = change(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun delete(id: String) {
        save(all.filterNot { it.id == id })
    }

    fun contains(id: String, item: String): Boolean = find(id)?.items?.contains(item) == true

    fun toggle(id: String, item: String) = change(id) {
        if (item in it.items) it.copy(items = it.items - item) else it.copy(items = it.items + item)
    }

    fun removeItem(id: String, index: Int) = change(id) {
        it.copy(items = it.items.filterIndexed { i, _ -> i != index })
    }

    fun move(id: String, from: Int, to: Int) = change(id) {
        if (from !in it.items.indices || to !in it.items.indices) it
        else it.copy(items = it.items.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Drops an article from every collection, after it is deleted from the library. */
    fun forget(item: String) {
        save(all.map { c -> c.copy(items = c.items.filterNot { it == item }) })
    }

    private fun change(id: String, update: (ArticleCollection) -> ArticleCollection) {
        save(all.map { if (it.id == id) update(it) else it })
    }

    private fun save(list: List<ArticleCollection>) {
        all = list
        try {
            file.writeText(CollectionFormat.serialize(list))
        } catch (t: Throwable) {
            DebugLog.e("Could not save collections", t)
        }
    }
}
