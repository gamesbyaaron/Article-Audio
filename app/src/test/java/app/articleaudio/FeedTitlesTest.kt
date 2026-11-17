package app.articleaudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTitlesTest {
    @Test fun ordinaryArticles() {
        assertTrue(FeedTitles.isArticle("Alan Turing"))
        assertTrue(FeedTitles.isArticle("Star Wars: A New Hope"))
    }

    @Test fun mainPageAndSpecialPagesAreLeftOut() {
        assertFalse(FeedTitles.isArticle("Main Page"))
        assertFalse(FeedTitles.isArticle("Special:Search"))
        assertFalse(FeedTitles.isArticle("Wikipedia:Featured articles"))
        assertFalse(FeedTitles.isArticle("File:Example.jpg"))
    }
}
