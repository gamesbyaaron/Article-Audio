package app.articleaudio

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Talks to English Wikipedia: article text, search suggestions and lead pictures. The app's only network use. */
object WikipediaClient {
    private const val USER_AGENT = "ArticleAudio/0.1 (personal text-to-speech reader for Android)"

    data class Article(val title: String, val text: String)

    class FetchException(message: String) : IOException(message)

    /** One search suggestion. */
    data class SearchResult(val title: String, val description: String, val thumbnail: String?)

    /** A free-licensed lead picture for an article, with its credit line. */
    class Picture(val bytes: ByteArray, val credit: String)

    /** Articles whose titles start with [query], as Wikipedia's own search box suggests them. */
    fun search(query: String): List<SearchResult> {
        val json = getJson(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2" +
                "&generator=prefixsearch&gpsnamespace=0&gpslimit=12&redirects=1" +
                "&prop=pageimages|description&piprop=thumbnail&pithumbsize=120&pilicense=free" +
                "&gpssearch=" + URLEncoder.encode(query, "UTF-8")
        )
        val pages = json.optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        val results = mutableListOf<Pair<Int, SearchResult>>()
        for (i in 0 until pages.length()) {
            val page = pages.getJSONObject(i)
            results += page.optInt("index", i) to SearchResult(
                title = page.optString("title"),
                description = page.optString("description"),
                thumbnail = page.optJSONObject("thumbnail")?.optString("source")?.ifEmpty { null },
            )
        }
        return results.sortedBy { it.first }.map { it.second }
    }

    /**
     * Today's featured article and most-read titles from Wikipedia's daily feed, for a UTC day
     * written "2026/11/17". Null if that day's feed is not there yet. Tries the feed's address
     * on Wikipedia first and Wikimedia's API address second.
     */
    fun fetchFeed(day: String): Pair<String?, List<String>>? {
        val addresses = listOf(
            "https://en.wikipedia.org/api/rest_v1/feed/featured/$day",
            "https://api.wikimedia.org/feed/v1/wikipedia/en/featured/$day",
        )
        for (address in addresses) {
            val json = try {
                getJson(address)
            } catch (t: Throwable) {
                DebugLog.e("Feed not available at $address: ${t.message}")
                continue
            }
            fun title(o: JSONObject): String? =
                o.optJSONObject("titles")?.optString("normalized")?.ifEmpty { null }
                    ?: o.optString("title").ifEmpty { null }?.replace('_', ' ')
            val tfa = json.optJSONObject("tfa")?.let { title(it) }
            val articles = json.optJSONObject("mostread")?.optJSONArray("articles")
            val mostRead = (0 until (articles?.length() ?: 0)).mapNotNull { articles!!.optJSONObject(it)?.let { o -> title(o) } }
            if (tfa == null && mostRead.isEmpty()) continue
            return tfa to mostRead
        }
        return null
    }

    /** Short descriptions and free-licensed thumbnails for several titles at once. */
    fun details(titles: List<String>): Map<String, SearchResult> {
        if (titles.isEmpty()) return emptyMap()
        val json = getJson(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&redirects=1" +
                "&prop=pageimages|description&piprop=thumbnail&pithumbsize=320&pilicense=free" +
                "&titles=" + URLEncoder.encode(titles.take(50).joinToString("|"), "UTF-8")
        )
        val query = json.optJSONObject("query") ?: return emptyMap()
        // Map each asked-for title through any normalising or redirect to the page that answered.
        val renamed = mutableMapOf<String, String>()
        for (key in listOf("normalized", "redirects")) {
            val list = query.optJSONArray(key) ?: continue
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                renamed[o.optString("from")] = o.optString("to")
            }
        }
        val pages = query.optJSONArray("pages") ?: return emptyMap()
        val byTitle = (0 until pages.length()).map { pages.getJSONObject(it) }.associateBy { it.optString("title") }
        return titles.mapNotNull { asked ->
            var t = asked
            repeat(2) { renamed[t]?.let { t = it } }
            val page = byTitle[t] ?: return@mapNotNull null
            asked to SearchResult(
                title = page.optString("title"),
                description = page.optString("description"),
                thumbnail = page.optJSONObject("thumbnail")?.optString("source")?.ifEmpty { null },
            )
        }.toMap()
    }

    /** Articles about a topic, most relevant first, from Wikipedia's full-text search. */
    fun searchTopic(query: String): List<SearchResult> {
        val json = getJson(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2" +
                "&generator=search&gsrnamespace=0&gsrlimit=20" +
                "&prop=pageimages|description&piprop=thumbnail&pithumbsize=120&pilicense=free" +
                "&gsrsearch=" + URLEncoder.encode(query, "UTF-8")
        )
        val pages = json.optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        val results = mutableListOf<Pair<Int, SearchResult>>()
        for (i in 0 until pages.length()) {
            val page = pages.getJSONObject(i)
            results += page.optInt("index", i) to SearchResult(
                title = page.optString("title"),
                description = page.optString("description"),
                thumbnail = page.optJSONObject("thumbnail")?.optString("source")?.ifEmpty { null },
            )
        }
        return results.sortedBy { it.first }.map { it.second }
    }

    /**
     * The article's lead picture, only if it is under a free licence (Wikipedia also shows
     * non-free pictures, which may not be reused). Null if there is none.
     */
    fun fetchPicture(title: String): Picture? {
        val json = getJson(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&redirects=1" +
                "&prop=pageimages&piprop=thumbnail|name&pithumbsize=320&pilicense=free" +
                "&titles=" + URLEncoder.encode(title, "UTF-8")
        )
        val page = json.optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0) ?: return null
        val source = page.optJSONObject("thumbnail")?.optString("source")?.ifEmpty { null } ?: return null
        val file = page.optString("pageimage").ifEmpty { null } ?: return null

        val info = getJson(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2" +
                "&prop=imageinfo&iiprop=extmetadata&iiextmetadatafilter=Artist|LicenseShortName|NonFree" +
                "&titles=" + URLEncoder.encode("File:$file", "UTF-8")
        )
        val meta = info.optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)
            ?.optJSONArray("imageinfo")?.optJSONObject(0)?.optJSONObject("extmetadata")
        fun value(key: String) = meta?.optJSONObject(key)?.optString("value")?.ifEmpty { null }
        if (value("NonFree")?.lowercase() == "true") return null

        return Picture(getBytes(source), ImageCredit.line(value("Artist"), value("LicenseShortName")))
    }

    /** Downloads a picture, such as a search thumbnail. */
    fun getBytes(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code != 200) throw FetchException("Picture download answered with HTTP $code")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun getJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code != 200) throw FetchException("Wikipedia answered with HTTP $code")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            json.optJSONObject("error")?.let {
                throw FetchException("Wikipedia error: ${it.optString("info", it.toString())}")
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    fun fetchPlainText(title: String): Article {
        val url = URL(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2" +
                "&prop=extracts&explaintext=1&exsectionformat=wiki&redirects=1" +
                "&titles=" + URLEncoder.encode(title, "UTF-8")
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code != 200) throw FetchException("Wikipedia answered with HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }

            val json = JSONObject(body)
            json.optJSONObject("error")?.let {
                throw FetchException("Wikipedia error: ${it.optString("info", it.toString())}")
            }
            val pages = json.optJSONObject("query")?.optJSONArray("pages")
                ?: throw FetchException("Unexpected answer from Wikipedia")
            val page = pages.getJSONObject(0)
            if (page.optBoolean("missing")) throw FetchException("No article called \"$title\"")
            if (page.optBoolean("invalid")) throw FetchException("\"$title\" is not a valid article title")
            val text = page.optString("extract")
            if (text.isBlank()) throw FetchException("The article has no text to read")
            return Article(page.optString("title", title), text)
        } finally {
            connection.disconnect()
        }
    }
}
