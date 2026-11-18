package app.articleaudio

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionFormatTest {
    @Test
    fun survivesSaving() {
        val list = listOf(
            ArticleCollection("c1", "Roman history", listOf("Julius Caesar", "Colosseum")),
            ArticleCollection("c2", "Empty one", emptyList()),
        )
        assertEquals(list, CollectionFormat.parse(CollectionFormat.serialize(list)))
    }

    @Test
    fun tabsAndLineBreaksInNamesAreFlattened() {
        val list = listOf(ArticleCollection("c1", "Space\tand\nstars", listOf("Mars")))
        assertEquals("Space and stars", CollectionFormat.parse(CollectionFormat.serialize(list)).single().name)
    }

    @Test
    fun junkLinesAreIgnored() = assertEquals(emptyList<ArticleCollection>(), CollectionFormat.parse("hello\n\nC\tonly-id"))
}
