package app.articleaudio

/**
 * Where each section starts in a downloaded audio file, saved next to the file as lines of
 * "sentence milliseconds". Pure Kotlin, unit tested.
 */
object SectionTimes {
    fun format(times: Map<Int, Long>): String =
        times.entries.sortedBy { it.key }.joinToString("\n") { "${it.key} ${it.value}" }

    fun parse(text: String): Map<Int, Long> = text.lines().mapNotNull { line ->
        val parts = line.trim().split(' ')
        val sentence = parts.getOrNull(0)?.toIntOrNull()
        val ms = parts.getOrNull(1)?.toLongOrNull()
        if (sentence != null && ms != null) sentence to ms else null
    }.toMap()

    /**
     * The time a section starts: recorded if known, otherwise estimated from how far through
     * the text it is (for files downloaded before times were recorded).
     */
    fun startMs(article: Article, sentence: Int, recorded: Map<Int, Long>, durationMs: Int): Int =
        recorded[sentence]?.toInt()
            ?: (durationMs.toLong() * article.startChars[sentence] / maxOf(1, article.totalChars)).toInt()
}
