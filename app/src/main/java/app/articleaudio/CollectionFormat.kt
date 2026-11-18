package app.articleaudio

/** A named, ordered group of library articles, like a playlist. */
data class ArticleCollection(val id: String, val name: String, val items: List<String>)

/** Saves and reads collections as plain text. Pure Kotlin, unit tested. */
object CollectionFormat {
    fun serialize(list: List<ArticleCollection>): String = list.joinToString("\n") { c ->
        val name = c.name.replace(Regex("[\\t\\r\\n]"), " ")
        (listOf("C", c.id, name) + c.items).joinToString("\t")
    }

    fun parse(text: String): List<ArticleCollection> = text.lines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 3 || parts[0] != "C") null
        else ArticleCollection(parts[1], parts[2], parts.drop(3).filter { it.isNotEmpty() })
    }
}
