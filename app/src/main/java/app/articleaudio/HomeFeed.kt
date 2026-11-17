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
    /** A topic tile: the name shown, what is searched for, and its icon if it has no picture. */
    data class Topic(val label: String, val query: String, val category: String)

    /** Placeholders until the owner's real list. */
    val TOPICS = listOf(
        Topic("Ancient Rome", "Ancient Rome", "history"),
        Topic("Space", "space exploration", "space"),
        Topic("World War II", "World War II", "war"),
        Topic("Dinosaurs", "dinosaurs", "animal"),
        Topic("Inventions", "inventions", "science"),
        Topic("Ocean life", "marine life", "animal"),
        Topic("Renaissance", "Renaissance art", "history"),
        Topic("Philosophy", "philosophy", "person"),
    )

    /** For each topic label, the address of a free picture from one of its first articles. */
    var topicPictures by mutableStateOf<Map<String, String>>(emptyMap())
        private set

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
                val todayFeed = WikipediaClient.fetchFeed(today)
                // Early in the day the feed may have the featured article but no most-read list yet.
                val yesterdayFeed = if (todayFeed == null || todayFeed.second.isEmpty()) WikipediaClient.fetchFeed(day(-1)) else null
                if (todayFeed == null && yesterdayFeed == null) {
                    throw WikipediaClient.FetchException("Wikipedia's daily feed is not available")
                }
                val tfa = todayFeed?.first ?: yesterdayFeed?.first
                val mostRead = todayFeed?.second?.ifEmpty { null } ?: yesterdayFeed?.second.orEmpty()
                if (todayFeed != null && todayFeed.second.isEmpty()) {
                    DebugLog.i("Today's feed has no most-read list yet; using yesterday's (${mostRead.size} titles)")
                }
                val wanted = listOfNotNull(tfa) + mostRead.filter { FeedTitles.isArticle(it) && it != tfa }.take(20)
                // The feed's own pictures may be non-free, so ask again for free-licensed ones only.
                val details = WikipediaClient.details(wanted)
                val f = tfa?.let { details[it] ?: WikipediaClient.SearchResult(it, "", null) }
                val p = wanted.drop(if (tfa != null) 1 else 0).map { details[it] ?: WikipediaClient.SearchResult(it, "", null) }
                val pictures = topicPictures()
                // Only call the day done when both parts arrived, so an incomplete day is retried.
                val complete = f != null && p.isNotEmpty()
                writeCache(if (complete) today else "", f, p, pictures)
                main.post {
                    featured = f
                    popular = p
                    topicPictures = pictures
                    loadedDay = if (complete) today else ""
                    loading = false
                }
                DebugLog.i("Home feed for $today: featured \"${f?.title}\", ${p.size} popular articles, ${pictures.size} topic pictures")
            } catch (t: Throwable) {
                DebugLog.e("Could not load the Home feed", t)
                main.post {
                    loading = false
                    problem = if (featured == null) "Could not load today's articles. Check the internet connection." else ""
                }
            }
        }
    }

    /** For each topic, the first free picture among its top articles. Failures just leave the icon. */
    private fun topicPictures(): Map<String, String> = TOPICS.mapNotNull { topic ->
        try {
            WikipediaClient.searchTopic(topic.query).firstNotNullOfOrNull { it.thumbnail }?.let { topic.label to it }
        } catch (t: Throwable) {
            DebugLog.e("No picture for topic ${topic.label}: ${t.message}")
            null
        }
    }.toMap()

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

    private fun writeCache(today: String, f: WikipediaClient.SearchResult?, p: List<WikipediaClient.SearchResult>, pictures: Map<String, String>) {
        try {
            val json = JSONObject().put("day", today).put("popular", JSONArray(p.map { toJson(it) }))
                .put("topicPictures", JSONObject(pictures))
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
            val pics = json.optJSONObject("topicPictures")
            topicPictures = pics?.keys()?.asSequence()?.associateWith { pics.optString(it) }.orEmpty()
        } catch (t: Throwable) {
            DebugLog.e("Could not read the saved Home feed", t)
        }
    }
}
