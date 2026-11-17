package app.articleaudio

/** Turns Wikimedia's image details into a short credit line. Pure Kotlin, unit tested. */
object ImageCredit {
    private val tag = Regex("<[^>]*>")
    private val space = Regex("\\s+")

    /** Wikimedia gives the author as HTML, often a link; this keeps only the words. */
    fun plain(html: String): String = tag.replace(html, " ")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .let { space.replace(it, " ").trim() }

    /** "Picture: Jane Smith, CC BY-SA 4.0, via Wikimedia Commons". */
    fun line(artistHtml: String?, license: String?): String {
        val artist = artistHtml?.let { plain(it) }?.take(120).orEmpty()
        val parts = listOfNotNull(artist.ifEmpty { null }, license?.trim()?.ifEmpty { null })
        return if (parts.isEmpty()) "Picture via Wikimedia Commons"
        else "Picture: ${parts.joinToString(", ")}, via Wikimedia Commons"
    }
}
