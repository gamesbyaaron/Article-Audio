package app.articleaudio

import app.articleaudio.WikiLink.Result.Article
import app.articleaudio.WikiLink.Result.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiLinkTest {
    private fun title(input: String) = (WikiLink.parse(input) as Article).title

    @Test fun desktopLink() = assertEquals("Alan Turing", title("https://en.wikipedia.org/wiki/Alan_Turing"))

    @Test fun mobileLink() = assertEquals("Alan Turing", title("https://en.m.wikipedia.org/wiki/Alan_Turing"))

    @Test fun dropsSectionAndQuery() =
        assertEquals("Alan Turing", title("https://en.wikipedia.org/wiki/Alan_Turing?foo=1#Early_life"))

    @Test fun decodesPercentEncoding() =
        assertEquals("Café Society", title("https://en.wikipedia.org/wiki/Caf%C3%A9_Society"))

    @Test fun keepsPlusSigns() = assertEquals("C++", title("https://en.wikipedia.org/wiki/C++"))

    @Test fun keepsParentheses() =
        assertEquals("Mercury (planet)", title("https://en.wikipedia.org/wiki/Mercury_(planet)"))

    @Test fun linkInsideSharedText() =
        assertEquals("Alamo Mission", title("Alamo Mission https://en.wikipedia.org/wiki/Alamo_Mission"))

    @Test fun indexPhpTitleLink() =
        assertEquals("Alan Turing", title("https://en.wikipedia.org/w/index.php?title=Alan_Turing&oldid=1"))

    @Test fun rejectsEmpty() = assertTrue(WikiLink.parse("  ") is Problem)

    @Test fun rejectsTextWithoutLink() = assertTrue(WikiLink.parse("Alan Turing") is Problem)

    @Test fun rejectsOtherSites() = assertTrue(WikiLink.parse("https://example.com/wiki/Alan_Turing") is Problem)

    @Test fun rejectsOtherLanguages() =
        assertTrue(WikiLink.parse("https://de.wikipedia.org/wiki/Alan_Turing") is Problem)

    @Test fun rejectsMainPageWithoutTitle() = assertTrue(WikiLink.parse("https://en.wikipedia.org/") is Problem)
}
