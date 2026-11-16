package app.articleaudio

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Fetches an article as plain text from English Wikipedia's API. The app's only network call. */
object WikipediaClient {
    private const val USER_AGENT = "ArticleAudio/0.1 (personal text-to-speech reader for Android)"

    data class Article(val title: String, val text: String)

    class FetchException(message: String) : IOException(message)

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
