package app.articleaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleTextTest {
    private fun texts(title: String, extract: String) = ArticleText.build(title, extract).sentences.map { it.text }

    @Test
    fun titleThenIntroThenSpokenHeadings() {
        val extract = """
            Intro one. Intro two.

            == History ==
            Started here.
        """.trimIndent()
        assertEquals(
            listOf("Topic.", "Intro one.", "Intro two.", "Section: History.", "Started here."),
            texts("Topic", extract),
        )
    }

    @Test
    fun paragraphsAreSpokenWhole() {
        val article = ArticleText.build("Topic", "One. Two. Three.\nFour.\n== History ==\nFive.")
        assertEquals(
            listOf("Topic.", "One. Two. Three.", "Four.", "Section: History.", "Five."),
            article.chunks.map { it.text },
        )
        assertEquals(listOf(true, false, false, true, false), article.chunks.map { it.isHeading })
    }

    @Test
    fun sentencesKnowTheirPlaceInTheParagraph() {
        val article = ArticleText.build("Topic", "One. Two. Three.")
        val chunk = article.chunks[1]
        for (s in article.sentences.filter { it.chunk == 1 }) {
            assertEquals(s.text, chunk.text.substring(s.start, s.end))
        }
        assertEquals(1, article.sentenceAt(1, 0))
        assertEquals(2, article.sentenceAt(1, 5))
        assertEquals(2, article.sentenceAt(1, 8))
        assertEquals(3, article.sentenceAt(1, 10))
        assertEquals(3, article.sentenceAt(1, 999))
    }

    @Test
    fun positionInTheWholeArticle() {
        val article = ArticleText.build("T", "One. Two.\nThree.")
        // Sentences: "T." "One." "Two." "Three."
        assertEquals(listOf(0, 2, 6, 10), article.startChars.toList())
        assertEquals(16, article.totalChars)
        assertEquals(0, article.sentenceAtChar(0))
        assertEquals(1, article.sentenceAtChar(5))
        assertEquals(2, article.sentenceAtChar(6))
        assertEquals(3, article.sentenceAtChar(999))
    }

    @Test
    fun longParagraphsAreSplitBetweenSentences() {
        val article = ArticleText.build("T", "Aaaa bbbb. Cccc dddd. Eeee ffff.", maxChars = 22)
        assertEquals(listOf("T.", "Aaaa bbbb. Cccc dddd.", "Eeee ffff."), article.chunks.map { it.text })
        assertTrue(article.chunks.all { it.text.length <= 22 })
        assertEquals(listOf(0, 1, 1, 2), article.sentences.map { it.chunk })
        assertEquals(listOf(0, 0, 11, 0), article.sentences.map { it.start })
    }

    @Test
    fun cleanupRunsAndRecordsChanges() {
        val article = ArticleText.build("Topic", "It ran 1964–1968.[1]\n== Years 1970–1975 ==\nMore.")
        assertEquals(
            listOf("Topic.", "It ran 1964 to 1968.", "Section: Years 1970 to 1975.", "More."),
            article.sentences.map { it.text },
        )
        assertEquals(3, article.changes.size)
    }

    @Test
    fun cleanupKeepsSentencesTogetherAfterCirca() = assertEquals(
        listOf("Topic.", "Built circa 1200 by monks.", "Then abandoned."),
        texts("Topic", "Built c. 1200 by monks. Then abandoned."),
    )

    @Test
    fun skipsReferenceSectionsAndTheirSubsections() {
        val extract = """
            Body.
            == See also ==
            == References ==
            === Sources ===
            Smith, J. (2001). A book.
            == Legacy ==
            Remembered.
        """.trimIndent()
        assertEquals(listOf("Topic.", "Body.", "Section: Legacy.", "Remembered."), texts("Topic", extract))
    }

    @Test
    fun keepsHeadingWhoseSubsectionHasText() {
        val extract = "Body.\n== Life ==\n=== Early life ===\nBorn.\n== Empty ==\n"
        assertEquals(
            listOf("Topic.", "Body.", "Section: Life.", "Section: Early life.", "Born."),
            texts("Topic", extract),
        )
    }

    @Test
    fun splitsSentences() = assertEquals(
        listOf("It rained.", "Then it stopped!", "Why?", "Nobody knows."),
        ArticleText.sentences("It rained. Then it stopped! Why? Nobody knows."),
    )

    @Test
    fun doesNotSplitAfterAbbreviations() = assertEquals(
        listOf("Dr. Smith met St. John, e.g. at No. 3 on Main St. in c. 1200.", "Then left."),
        ArticleText.sentences("Dr. Smith met St. John, e.g. at No. 3 on Main St. in c. 1200. Then left."),
    )

    @Test
    fun doesNotSplitAfterInitials() = assertEquals(
        listOf("J. R. R. Tolkien was born in the U.S. Army hospital.", "No."),
        ArticleText.sentences("J. R. R. Tolkien was born in the U.S. Army hospital. No."),
    )

    @Test
    fun doesNotSplitBeforeLowercase() = assertEquals(
        listOf("Version 2.0 is out. version 3 is not."),
        ArticleText.sentences("Version 2.0 is out. version 3 is not."),
    )

    @Test
    fun splitsAfterClosingQuote() = assertEquals(
        listOf("He said \"Go.\"", "They went."),
        ArticleText.sentences("He said \"Go.\" They went."),
    )

    @Test
    fun longSentencesBreakAtCommas() {
        val parts = ArticleText.splitLong("aaaa bbbb, cccc dddd, eeee", 12)
        assertEquals(listOf("aaaa bbbb,", "cccc dddd,", "eeee"), parts)
        assertFalse(parts.any { it.length > 12 })
    }

    @Test
    fun longWordsAreCutHard() =
        assertEquals(listOf("abcde", "fghij", "k"), ArticleText.splitLong("abcdefghijk", 5))
}
