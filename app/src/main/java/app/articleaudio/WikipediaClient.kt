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
