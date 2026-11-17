package app.articleaudio

/**
 * Picks a generic icon for an article that has no free picture, from its short description
 * ("2024 video game", "1955–1975 conflict in Vietnam") or, failing that, its title.
 * Pure Kotlin, unit tested.
 */
object Category {
    // Checked as whole words, with an optional plural "s". Longer phrases are listed where a
    // single word would be misleading ("video game", "football club").
    private val KEYWORDS: List<Pair<String, List<String>>> = listOf(
        "game" to listOf("video game", "game", "board game", "card game", "game series", "esports"),
        "war" to listOf("war", "battle", "conflict", "siege", "invasion", "military operation", "campaign", "massacre", "uprising", "rebellion", "civil war"),
        "person" to listOf(
            "actor", "actress", "singer", "songwriter", "rapper", "musician", "composer", "politician", "writer",
            "author", "poet", "novelist", "painter", "artist", "sculptor", "philosopher", "scientist", "mathematician",
            "physicist", "chemist", "biologist", "astronomer", "engineer", "inventor", "businessman", "businesswoman",
            "entrepreneur", "president", "king", "queen", "emperor", "empress", "prime minister", "monarch", "pope",
            "saint", "general", "explorer", "footballer", "player", "athlete", "director", "filmmaker", "journalist",
            "comedian", "youtuber", "character", "personality", "activist", "lawyer", "judge", "economist", "historian",
            "pharaoh", "sultan", "tsar", "chancellor", "officer", "soldier", "pilot", "astronaut", "model", "dancer",
        ),
        "film" to listOf("film", "movie", "television series", "tv series", "sitcom", "miniseries", "anime", "documentary", "television program", "television show"),
        "music" to listOf("album", "song", "band", "music", "opera", "symphony", "musical", "ep", "soundtrack"),
        "book" to listOf("novel", "book", "poem", "comic", "manga", "play", "short story", "magazine", "newspaper", "series of novels"),
        "sport" to listOf("football club", "sports team", "team", "club", "sport", "league", "championship", "olympics", "tournament", "cup", "stadium", "race"),
        "animal" to listOf("species", "genus", "animal", "bird", "fish", "mammal", "reptile", "dinosaur", "insect", "breed", "family of", "order of", "amphibian", "spider", "shark", "whale"),
        "space" to listOf("planet", "star", "galaxy", "spacecraft", "moon", "asteroid", "comet", "constellation", "space mission", "space telescope", "satellite", "nebula", "dwarf planet"),
        "science" to listOf("chemical element", "element", "disease", "disorder", "theory", "protein", "chemical compound", "compound", "physical quantity", "branch of science", "field of study", "virus", "bacterium", "drug", "medication"),
        "nature" to listOf("mountain", "river", "lake", "volcano", "forest", "national park", "sea", "ocean", "desert", "waterfall", "glacier", "bay", "peninsula", "valley", "reef"),
        "place" to listOf("city", "town", "village", "country", "capital", "state", "province", "island", "region", "municipality", "county", "district", "continent", "neighbourhood", "neighborhood", "metropolis", "archipelago"),
        "history" to listOf("empire", "dynasty", "kingdom", "civilization", "civilisation", "period", "era", "revolution", "treaty", "political party", "election", "government", "republic", "monarchy", "ancient", "age"),
        "building" to listOf("building", "castle", "church", "cathedral", "temple", "palace", "bridge", "tower", "monument", "museum", "skyscraper", "mosque", "fortress"),
    )

    private val CUT = Regex("""\s(by|of|in|from|for|on|at|to|who|which|that|with|and|or)\s|[,;(]""")

    fun of(description: String, title: String = ""): String =
        fromText(description) ?: fromText(title) ?: "article"

    /**
     * Uses the head of the phrase: the words before the first "by", "of", "in" and so on
     * ("2019 song by American singer" → "2019 song"), and within that the last keyword
     * ("American film director" → "director").
     */
    private fun fromText(text: String): String? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val head = CUT.find(lower)?.let { lower.substring(0, it.range.first) }?.ifBlank { null } ?: lower
        var best: String? = null
        var bestEnd = -1
        for ((category, words) in KEYWORDS) {
            for (word in words) {
                for (m in Regex("""\b${Regex.escape(word)}s?\b""").findAll(head)) {
                    // The keyword that ends last wins; on a tie the longer phrase does.
                    if (m.range.last > bestEnd) {
                        bestEnd = m.range.last
                        best = category
                    }
                }
            }
        }
        return best
    }
}
