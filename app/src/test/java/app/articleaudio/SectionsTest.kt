package app.articleaudio

import org.junit.Assert.assertEquals
import org.junit.Test

class SectionsTest {
    private val article = ArticleText.build("T", "Intro.\n== Life ==\nBorn.\n== Work ==\nWrote.")

    @Test
    fun listsBeginningThenHeadings() = assertEquals(
        listOf(ArticleSection(0, "Beginning"), ArticleSection(2, "Life"), ArticleSection(4, "Work")),
        article.sections,
    )

    @Test
    fun knowsWhichSectionASentenceIsIn() {
        assertEquals(0, article.sectionAt(1))
        assertEquals(1, article.sectionAt(3))
        assertEquals(2, article.sectionAt(5))
    }

    @Test
    fun recordedTimesSurviveSaving() {
        val times = mapOf(0 to 0L, 2 to 4_500L, 4 to 9_000L)
        assertEquals(times, SectionTimes.parse(SectionTimes.format(times)))
    }

    @Test
    fun recordedTimeIsUsedWhenKnown() = assertEquals(4_500, SectionTimes.startMs(article, 2, mapOf(2 to 4_500L), 20_000))

    @Test
    fun otherwiseTimeIsEstimatedFromTheText() {
        val expected = 20_000L * article.startChars[4] / article.totalChars
        assertEquals(expected.toInt(), SectionTimes.startMs(article, 4, emptyMap(), 20_000))
    }
}
