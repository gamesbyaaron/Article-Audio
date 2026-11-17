package app.articleaudio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

/**
 * Content for the Home tab: Wikipedia's Article of the Day and today's most-read articles,
 * fetched once a day and kept for offline use, plus the developer's topic list.
 */
object HomeFeed {
    /** Topic tiles: the name shown, and what is searched for. Placeholders until the real list. */
    val TOPICS = listOf(
        "Ancient Rome" to "Ancient Rome",
        "Space" to "space exploration",
        "World War II" to "World War II",
        "Dinosaurs" to "dinosaurs",
        "Inventions" to "inventions",
        "Ocean life" to "marine life",
        "Renaissance" to "Renaissance art",
        "Philosophy" to "philosophy",
    )

    var featured by mutableStateOf<WikipediaClient.SearchResult?>(null)
        private set
    var popular by mutableStateOf<List<WikipediaClient.SearchResult>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var problem by mutableStateOf("")
        private set

    private lateinit var cache: File
    private val main = Handler(Looper.getMainLooper())
    private var loadedDay = ""

    fun init(context: Context) {
        cache = File(context.filesDir, "home.json")
        readCache()
    }

    /** Fetches today's content unless it is already here. Shows the saved copy if offline. */
    fun refresh(force: Boolean = false) {
        val today = day(0)
        if (loading || (!force && loadedDay == today)) return
        loading = true
        problem = ""
        thread(name = "home") {
            try {
                val feed = WikipediaClient.fetchFeed(today) ?: WikipediaClient.fetchFeed(day(-1))
                    ?: throw WikipediaClient.FetchException("Wikipedia's daily feed is not available")
                val (tfa, mostRead) = feed
                val wanted = listOfNotNull(tfa) + mostRead.filter { isArticleTitle(it) && it != tfa }.take(20)
                // The feed's own pictures may be non-free, so ask again for free-licensed ones only.
                val details = WikipediaClient.details(wanted)
                val f = tfa?.let { details[it] ?: WikipediaClient.SearchResult(it, "", null) }
                val p = wanted.drop(if (tfa != null) 1 else 0).map { details[it] ?: WikipediaClient.SearchResult(it, "", null) }
                writeCache(today, f, p)
                main.post {
                    featured = f
                    popular = p
                    loadedDay = today
                    loading = false
                }
                DebugLog.i("Home feed for $today: featured \"${f?.title}\", ${p.size} popular articles")
            } catch (t: Throwable) {
                DebugLog.e("Could not load the Home feed", t)
                main.post {
                    loading = false
                    problem = if (featured == null) "Could not load today's articles. Check the internet connection." else ""
                }
            }
        }
    }

    /** Leaves out the Main Page and special or maintenance pages that appear in the most-read list. */
    fun isArticleTitle(title: String): Boolean {
        if (title == "Main Page") return false
        val prefix = title.substringBefore(':', "")
        return prefix !in setOf("Special", "Wikipedia", "File", "Portal", "Help", "Template", "Category", "Talk", "User")
    }

    /** The date in UTC, as Wikipedia's feed uses: "2026/11/17". */
    private fun day(offsetDays: Int): String {
        val format = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(System.currentTimeMillis() + offsetDays * 86_400_000L))
    }

    private fun toJson(r: WikipediaClient.SearchResult) = JSONObject()
        .put("title", r.title).put("description", r.description).put("thumbnail", r.thumbnail ?: "")

    private fun fromJson(o: JSONObject) = WikipediaClient.SearchResult(
        o.optString("title"), o.optString("description"), o.optString("thumbnail").ifEmpty { null },
    )

    private fun writeCache(today: String, f: WikipediaClient.SearchResult?, p: List<WikipediaClient.SearchResult>) {
        try {
            val json = JSONObject().put("day", today).put("popular", JSONArray(p.map { toJson(it) }))
            if (f != null) json.put("featured", toJson(f))
            cache.writeText(json.toString())
        } catch (t: Throwable) {
            DebugLog.e("Could not save the Home feed", t)
        }
    }

    private fun readCache() {
        try {
            if (!cache.exists()) return
            val json = JSONObject(cache.readText())
            featured = json.optJSONObject("featured")?.let { fromJson(it) }
            val list = json.optJSONArray("popular") ?: JSONArray()
            popular = (0 until list.length()).map { fromJson(list.getJSONObject(it)) }
            loadedDay = json.optString("day")
        } catch (t: Throwable) {
            DebugLog.e("Could not read the saved Home feed", t)
        }
    }
}
