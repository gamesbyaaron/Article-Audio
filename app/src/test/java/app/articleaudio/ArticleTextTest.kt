package app.articleaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ArticleTextTest {
    private fun texts(title: String, extract: String) = ArticleText.toUtterances(title, extract).map { it.text }

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
    fun headingsArePausePoints() {
        val result = ArticleText.toUtterances("Topic", "Text.\n== History ==\nMore.")
        assertEquals(listOf(true, false, true, false), result.map { it.isHeading })
    }

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
