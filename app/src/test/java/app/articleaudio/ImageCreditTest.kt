package app.articleaudio

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCreditTest {
    @Test fun authorLinkBecomesPlainText() = assertEquals(
        "Picture: Jane Smith, CC BY-SA 4.0, via Wikimedia Commons",
        ImageCredit.line("<a href=\"//commons.wikimedia.org/wiki/User:JS\" title=\"User:JS\">Jane   Smith</a>", "CC BY-SA 4.0"),
    )

    @Test fun entitiesAreDecoded() = assertEquals("Smith & Jones", ImageCredit.plain("Smith &amp; Jones"))

    @Test fun licenceOnly() = assertEquals(
        "Picture: Public domain, via Wikimedia Commons",
        ImageCredit.line(null, "Public domain"),
    )

    @Test fun nothingKnown() = assertEquals("Picture via Wikimedia Commons", ImageCredit.line("", null))
}
