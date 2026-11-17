package app.articleaudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedTitleTest {
    @Test fun ordinaryArticles() {
        assertTrue(HomeFeed.isArticleTitle("Alan Turing"))
        assertTrue(HomeFeed.isArticleTitle("Star Wars: A New Hope"))
    }

    @Test fun mainPageAndSpecialPagesAreLeftOut() {
        assertFalse(HomeFeed.isArticleTitle("Main Page"))
        assertFalse(HomeFeed.isArticleTitle("Special:Search"))
        assertFalse(HomeFeed.isArticleTitle("Wikipedia:Featured articles"))
        assertFalse(HomeFeed.isArticleTitle("File:Example.jpg"))
    }
}
