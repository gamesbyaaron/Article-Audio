package app.articleaudio

/**
 * Suggests topics from recently heard articles: Wikipedia categories that several of them
 * share ("Cold War conflicts"), then one broader category per recent article. Maintenance
 * categories are left out and names are tidied. Pure Kotlin, unit tested.
 */
object TopicPicker {
    /** A suggested topic: the name shown and the Wikipedia category behind it. */
    data class Picked(val label: String, val category: String)

    private val JUNK = listOf(
        "article", "pages", "wikipedia", "use ", "cs1", "webarchive", "short description", "births", "deaths",
        "living people", "stub", "disambiguation", "commons", "redirect", "template", "dates", "coordinates",
        "engvar", "harv", "sfn", "wikidata", "lacking", "needing", "containing", "infobox", "accuracy",
        "cleanup", "citation", "unsourced", "lists of", "list of", "good article", "featured", "spoken",
        "establishments", "disestablishments", "people from", "alumni", "burials", "recipients of",
    )
    private val YEARISH = Regex("""^\d{3,4}s?\b""")

    /** A readable topic name for a category, or null if it is not a real subject. */
    fun clean(category: String): String? {
        val name = category.removePrefix("Category:").trim()
        val lower = name.lowercase()
        if (name.isEmpty() || name.length > 40) return null
        if (JUNK.any { it in lower }) return null
        if (YEARISH.containsMatchIn(lower)) return null // "1912 in film", "1960s in Vietnam"
        return name.replaceFirstChar { it.uppercase() }
    }

    /**
     * [recent] holds each recently heard article's title and categories, newest first.
     * Returns up to [max] topics, most shared first.
     */
    fun pick(recent: List<Pair<String, List<String>>>, max: Int = 8): List<Picked> {
        class Candidate(val picked: Picked, var count: Int, val firstSeen: Int)

        val all = linkedMapOf<String, Candidate>()
        recent.forEachIndexed { i, (title, categories) ->
            for (raw in categories.distinct()) {
                val label = clean(raw) ?: continue
                // The article's own category ("Vietnam War") is not a new topic.
                if (label.equals(title, ignoreCase = true)) continue
                all.getOrPut(raw) { Candidate(Picked(label, raw), 0, i) }.count++
            }
        }

        val chosen = mutableListOf<Picked>()
        fun similar(a: String, b: String) = a.lowercase() in b.lowercase() || b.lowercase() in a.lowercase()
        fun take(p: Picked) {
            if (chosen.size < max && chosen.none { similar(it.label, p.label) || it.category == p.category }) chosen += p
        }

        // 1. Topics several recent articles have in common.
        all.values.filter { it.count >= 2 }
            .sortedWith(compareByDescending<Candidate> { it.count }.thenBy { it.firstSeen }.thenBy { it.picked.label.length })
            .forEach { take(it.picked) }

        // 2. Then one topic for each recent article, the shortest (usually broadest) name.
        for ((_, categories) in recent) {
            if (chosen.size >= max) break
            categories.mapNotNull { all[it]?.picked }
                .filter { p -> chosen.none { it.category == p.category } }
                .minByOrNull { it.label.length }
                ?.let { take(it) }
        }
        return chosen
    }
}
