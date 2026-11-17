package app.articleaudio

/** One sentence (or heading) as shown on screen, and where it sits inside its chunk. */
data class Sentence(val text: String, val chunk: Int, val start: Int, val isHeading: Boolean) {
    val end: Int get() = start + text.length
}

/**
 * A piece of text handed to the speech engine in one go: a heading, or a paragraph (or part of
 * a very long one). Speaking whole paragraphs lets the voice flow from sentence to sentence.
 */
class Chunk(val text: String, val isHeading: Boolean, val firstSentence: Int, val sentenceCount: Int)

/** A heading in the article, and the sentence it starts at. */
data class ArticleSection(val sentence: Int, val title: String)

class Article(
    val title: String,
    val chunks: List<Chunk>,
    val sentences: List<Sentence>,
    val changes: List<Change>,
) {
    /** "Beginning" followed by every spoken section heading, in order. */
    val sections: List<ArticleSection> by lazy {
        listOf(ArticleSection(0, "Beginning")) +
            sentences.withIndex()
                .filter { it.index > 0 && it.value.isHeading }
                .map { ArticleSection(it.index, it.value.text.removePrefix("Section: ").removeSuffix(".")) }
    }

    /** Position in [sections] of the section that contains [sentence]. */
    fun sectionAt(sentence: Int): Int = sections.indexOfLast { it.sentence <= sentence }.coerceAtLeast(0)

    /** For each sentence, how many characters of the article come before it. */
    val startChars: IntArray by lazy {
        var total = 0
        IntArray(sentences.size) { i -> total.also { total += sentences[i].text.length } }
    }

    val totalChars: Int by lazy { sentences.sumOf { it.text.length } }

    /** The sentence being read once [chars] characters of the article have been spoken. */
    fun sentenceAtChar(chars: Int): Int {
        var found = 0
        for (i in sentences.indices) {
            if (startChars[i] <= chars) found = i else break
        }
        return found
    }

    /** The sentence that contains character [offset] of chunk [chunk]. */
    fun sentenceAt(chunk: Int, offset: Int): Int {
        val c = chunks[chunk]
        var found = c.firstSentence
        for (i in c.firstSentence until c.firstSentence + c.sentenceCount) {
            if (sentences[i].start <= offset) found = i else break
        }
        return found
    }
}

/**
 * Turns Wikipedia's plain-text article (headings written as "== Title ==") into chunks and
 * sentences to speak, running the cleanup rules on the way. Pure Kotlin, unit tested.
 */
object ArticleText {
    /** Sections that are lists of sources or links, not prose. Their subsections are skipped too. */
    val SKIPPED_SECTIONS = setOf(
        "references", "notes", "citations", "see also", "external links", "further reading",
        "bibliography", "sources", "footnotes", "notes and references", "works cited",
    )

    private val heading = Regex("""^(={2,6})\s*(.+?)\s*\1\s*$""")

    private class Section(val level: Int, val title: String?, val paragraphs: MutableList<String> = mutableListOf())

    fun build(title: String, extract: String, maxChars: Int = 3000): Article {
        val chunks = mutableListOf<Chunk>()
        val sentences = mutableListOf<Sentence>()
        val changes = mutableListOf<Change>()

        fun clean(text: String): String {
            val result = Cleanup.apply(text)
            changes += result.changes
            return result.text
        }

        fun addHeading(text: String) {
            sentences += Sentence(text, chunks.size, 0, isHeading = true)
            chunks += Chunk(text, isHeading = true, firstSentence = sentences.size - 1, sentenceCount = 1)
        }

        fun addParagraph(paragraph: String) {
            val pieces = sentences(paragraph).flatMap { splitLong(it, maxChars) }
            var text = StringBuilder()
            var first = sentences.size
            fun flush() {
                if (text.isEmpty()) return
                chunks += Chunk(text.toString(), isHeading = false, firstSentence = first, sentenceCount = sentences.size - first)
                text = StringBuilder()
                first = sentences.size
            }
            for (piece in pieces) {
                if (text.isNotEmpty() && text.length + 1 + piece.length > maxChars) flush()
                if (text.isNotEmpty()) text.append(' ')
                sentences += Sentence(piece, chunks.size, text.length, isHeading = false)
                text.append(piece)
            }
            flush()
        }

        val sections = parseSections(extract)
        addHeading(withStop(title))
        for ((i, section) in sections.withIndex()) {
            if (section.title != null && hasContent(sections, i)) {
                addHeading("Section: ${withStop(clean(section.title))}")
            }
            for (paragraph in section.paragraphs) {
                val cleaned = clean(paragraph)
                if (cleaned.isNotEmpty()) addParagraph(cleaned)
            }
        }
        return Article(title, chunks, sentences, changes)
    }

    private fun parseSections(extract: String): List<Section> {
        val sections = mutableListOf(Section(level = 1, title = null))
        var skippedLevel = Int.MAX_VALUE // while skipping, the level of the skipped heading
        var skipping = false
        for (line in extract.lines()) {
            val m = heading.matchEntire(line.trim())
            if (m != null) {
                val level = m.groupValues[1].length
                val name = m.groupValues[2]
                if (skipping && level > skippedLevel) continue
                skipping = name.lowercase() in SKIPPED_SECTIONS
                if (skipping) {
                    skippedLevel = level
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
        if (text.isEmpty() || text.last() in ".!?") text else "$text."

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
