package app.articleaudio

import java.net.URLDecoder

/** Turns pasted or shared text into an English Wikipedia article title. Pure Kotlin, unit tested. */
object WikiLink {
    sealed class Result {
        data class Article(val title: String) : Result()
        data class Problem(val message: String) : Result()
    }

    private val anyUrl = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val wikiUrl = Regex(
        """^https?://([a-z0-9-]+)(\.m)?\.wikipedia\.org(/[^?#]*)?(\?[^#]*)?""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(input: String): Result {
        val text = input.trim()
        if (text.isEmpty()) return Result.Problem("Paste or share a Wikipedia link first.")
        val url = anyUrl.find(text)?.value ?: return Result.Problem("No link found in the text.")
        val match = wikiUrl.find(url) ?: return Result.Problem("That link is not a Wikipedia article.")
        val language = match.groupValues[1].lowercase()
        if (language != "en") return Result.Problem("Only English Wikipedia links work for now.")

        val path = match.groupValues[3]
        val query = match.groupValues[4].removePrefix("?")
        val raw = when {
            path.startsWith("/wiki/") -> path.removePrefix("/wiki/")
            else -> query.split('&').firstOrNull { it.startsWith("title=") }?.removePrefix("title=").orEmpty()
        }
        val title = decode(raw).replace('_', ' ').trim()
        if (title.isEmpty()) return Result.Problem("That link does not point to an article.")
        return Result.Article(title)
    }

    private fun decode(raw: String): String = try {
        // Keep literal plus signs: titles such as "C++" use them.
        URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
    } catch (e: IllegalArgumentException) {
        raw
    }
}
