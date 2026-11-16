package app.articleaudio

/** One piece of speech: a sentence, or a heading that gets a short pause after it. */
data class Utterance(val text: String, val isHeading: Boolean = false)

/**
 * Turns Wikipedia's plain-text article (headings written as "== Title ==") into a list of
 * sentences to speak. Pure Kotlin, unit tested.
 */
object ArticleText {
    /** Sections that are lists of sources or links, not prose. Their subsections are skipped too. */
    val SKIPPED_SECTIONS = setOf(
        "references", "notes", "citations", "see also", "external links", "further reading",
        "bibliography", "sources", "footnotes", "notes and references", "works cited",
    )

    private val heading = Regex("""^(={2,6})\s*(.+?)\s*\1\s*$""")

    private class Section(val level: Int, val title: String?, val paragraphs: MutableList<String> = mutableListOf())

    fun toUtterances(title: String, extract: String, maxChars: Int = 3000): List<Utterance> {
        val sections = parseSections(extract)
        val out = mutableListOf(Utterance(withStop(title), isHeading = true))
        for ((i, section) in sections.withIndex()) {
            if (section.title != null && hasContent(sections, i)) {
                out += Utterance("Section: ${withStop(section.title)}", isHeading = true)
            }
            for (paragraph in section.paragraphs) {
                for (sentence in sentences(paragraph)) {
                    splitLong(sentence, maxChars).forEach { out += Utterance(it) }
                }
            }
        }
        return out
    }

    private fun parseSections(extract: String): List<Section> {
        val sections = mutableListOf(Section(level = 1, title = null))
        var skipBelowOrAt = Int.MAX_VALUE // while skipping, the level of the skipped heading
        var skipping = false
        for (line in extract.lines()) {
            val m = heading.matchEntire(line.trim())
            if (m != null) {
                val level = m.groupValues[1].length
                val name = m.groupValues[2]
                if (skipping && level > skipBelowOrAt) continue
                skipping = name.lowercase() in SKIPPED_SECTIONS
                if (skipping) {
                    skipBelowOrAt = level
                    continue
                }
                sections += Section(level, name)
            } else if (!skipping && line.isNotBlank()) {
                sections.last().paragraphs += line.trim()
            }
        }
        return sections
    }

    /** A heading is spoken only if it, or one of its subsections, has text. */
    private fun hasContent(sections: List<Section>, index: Int): Boolean {
        val level = sections[index].level
        if (sections[index].paragraphs.isNotEmpty()) return true
        for (j in index + 1 until sections.size) {
            if (sections[j].level <= level) return false
            if (sections[j].paragraphs.isNotEmpty()) return true
        }
        return false
    }

    private fun withStop(text: String): String =
        if (text.last() in ".!?") text else "$text."

    private val ABBREVIATIONS = setOf(
        "mr.", "mrs.", "ms.", "dr.", "prof.", "st.", "jr.", "sr.", "vs.", "e.g.", "i.e.", "c.", "ca.",
        "no.", "nos.", "approx.", "inc.", "ltd.", "co.", "corp.", "mt.", "ft.", "gen.", "col.", "lt.",
        "sgt.", "capt.", "gov.", "sen.", "rep.", "rev.", "fr.", "b.", "d.", "r.", "fl.", "vol.", "pp.",
        "p.", "jan.", "feb.", "mar.", "apr.", "jun.", "jul.", "aug.", "sep.", "sept.", "oct.", "nov.", "dec.",
    )
    private val boundary = Regex("""[.!?]["'”’)\]]*\s+""")
    private val initials = Regex("""^\(?([A-Za-z]\.)+$""")

    /** Splits a paragraph into sentences, avoiding breaks after common abbreviations and initials. */
    fun sentences(paragraph: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        for (m in boundary.findAll(paragraph)) {
            val end = m.range.last + 1
            if (end >= paragraph.length) break
            val next = paragraph[end]
            if (!(next.isUpperCase() || next.isDigit() || next in "\"“'(")) continue
            val punctuationEnd = m.range.first + 1
            val lastWord = paragraph.substring(start, punctuationEnd).substringAfterLast(' ')
            if (paragraph[m.range.first] == '.' &&
                (lastWord.lowercase().trimStart('(') in ABBREVIATIONS || initials.matches(lastWord))
            ) continue
            result += paragraph.substring(start, end).trim()
            start = end
        }
        val rest = paragraph.substring(start).trim()
        if (rest.isNotEmpty()) result += rest
        return result
    }

    /** Breaks text longer than the speech engine's limit, preferring commas, then spaces. */
    fun splitLong(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val parts = mutableListOf<String>()
        var rest = text
        while (rest.length > maxChars) {
            val window = rest.substring(0, maxChars)
            val cut = listOf(window.lastIndexOf(", "), window.lastIndexOf("; "))
                .maxOrNull()?.takeIf { it > maxChars / 2 }?.plus(1)
                ?: window.lastIndexOf(' ').takeIf { it > 0 }
                ?: maxChars
            parts += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) parts += rest
        return parts
    }
}
