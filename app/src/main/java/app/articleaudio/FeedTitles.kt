package app.articleaudio

/** Which most-read titles are real articles. Pure Kotlin, unit tested. */
object FeedTitles {
    private val NOT_ARTICLES = setOf("Special", "Wikipedia", "File", "Portal", "Help", "Template", "Category", "Talk", "User")

    /** Leaves out the Main Page and special or maintenance pages that appear in the most-read list. */
    fun isArticle(title: String): Boolean {
        if (title == "Main Page") return false
        return title.substringBefore(':', "") !in NOT_ARTICLES
    }
}
